package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.{QueryByReactionId, QueryBySequenceId}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords, SequenceKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.mutable

trait SequenceIdToRxnInchis extends QueryBySequenceId with MongoWorkflowUtilities with QueryByReactionId {


  val logger = LogManager.getLogger("sequencesToInchis")

  def sequencesIdsToInchis(mongoConnection: MongoDB)
                          (sequences: Set[Long], chemicalKeywordToLookAt: String): Set[String] = {
    val reactionIds: Set[Long] = getReactionsCatalyzedBySequenceId(mongoConnection)(sequences)
    val chemicalIds: Set[Long] = getChemicalsByReactionId(mongoConnection)(reactionIds, chemicalKeywordToLookAt)
    getInchisFromChemicalIds(mongoConnection)(chemicalIds)
  }


  def getReactionsCatalyzedBySequenceId(mongoConnection: MongoDB)
                                       (sequences: Set[Long]): Set[Long] = {
    // Get all the sequences and their reactions
    val returnValues: Iterator[DBObject] =
      querySequencesBySequenceId(sequences.toList, mongoConnection, List(SequenceKeywords.RXN_REFS.toString))

    val rxnRefSet = mutable.Set[Long]()
    for (doc <- returnValues) {
      val rxnRefs = doc.get(SequenceKeywords.RXN_REFS.toString).asInstanceOf[BasicDBList]
      for (rxn <- rxnRefs.listIterator) {
        rxnRefSet.add(rxn.asInstanceOf[Long])
      }
    }
    rxnRefSet.toSet
  }

  def getChemicalsByReactionId(mongoConnection: MongoDB)
                              (reactionIds: Set[Long], chemicalKeywordToLookAt: String): Set[Long] = {
    // With each of those reactions, get the substrate's chem ids
    val chemicalSet = mutable.Set[Long]()
    for (reaction <- reactionIds) {

      val key = new BasicDBObject(ReactionKeywords.ID.toString, reaction)
      val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY.toString}.$chemicalKeywordToLookAt", 1)
      val iterator: Iterator[DBObject] = mongoQueryReactions(mongoConnection)(key, filter)

      for (substrate: DBObject <- iterator) {
        val enzSummary = substrate.get(ReactionKeywords.ENZ_SUMMARY.toString).asInstanceOf[BasicDBObject]
        val substrateList = enzSummary.get(chemicalKeywordToLookAt).asInstanceOf[BasicDBList]
        if (substrateList == null) {
          logger.error(s"Number of substrates for reaction is 0.  Reaction is $reaction")
        } else {
          for (substrate <- substrateList.listIterator().toIterator) {
            val chemId = substrate.asInstanceOf[BasicDBObject].get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long]
            chemicalSet.add(chemId)
          }
        }
      }
    }

    chemicalSet.toSet
  }

  def getInchisFromChemicalIds(mongoConnection: MongoDB)(chemicalIds: Set[Long]): Set[String] = {
    // With all the substrates in hand, we now need to find the inchis!
    val inchiSet = mutable.Set[String]()
    for (substrate <- chemicalIds) {
      val key = createDbObject(ChemicalKeywords.ID, substrate)
      val filter = createDbObject(ChemicalKeywords.INCHI, 1)
      val iterator: Iterator[DBObject] = mongoQueryChemicals(mongoConnection)(key, filter)

      // Only except 1 item from iterator
      for (chemical: DBObject <- iterator) {
        val inchi = chemical.get(ChemicalKeywords.INCHI.toString).asInstanceOf[String]
        if (!inchi.contains("FAKE")) {
          inchiSet.add(inchi)
        }
      }
    }

    inchiSet.toSet
  }
}
