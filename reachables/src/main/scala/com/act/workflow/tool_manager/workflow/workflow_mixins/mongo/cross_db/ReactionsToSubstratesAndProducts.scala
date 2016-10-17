package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import chemaxon.struc.Molecule
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicalInchi
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._

trait ReactionsToSubstratesAndProducts extends MongoWorkflowUtilities with QueryChemicalInchi {
  private val localLogger = LogManager.getLogger(getClass)

  def querySubstrateAndProductMoleculesByReactionIds(mongoConnection: MongoDB)(reactionIds: List[Long]): List[MoleculeReaction] ={
    val dbReactionIdsIterator = getReactionsById(mongoConnection)(reactionIds)

    // Now query for chemicals
    val chemicalQuery = getMoleculeById(mongoConnection)_
    val moleculeMap: List[MoleculeReaction] = dbReactionIdsIterator.toStream.par.flatMap(result => {
      val reactionId = result.get(ReactionKeywords.ID.toString)

      val enzSummary = result.get(ReactionKeywords.ENZ_SUMMARY.toString).asInstanceOf[DBObject]

      val substrateResult = enzSummary.get(ReactionKeywords.SUBSTRATES.toString).asInstanceOf[BasicDBList].toList.asInstanceOf[List[BasicDBObject]]
      val substrateMolecules = substrateResult.flatMap(r =>
        List.fill(r.get(ReactionKeywords.COEFFICIENT.toString).toString.toInt)(chemicalQuery(r.get(ReactionKeywords.PUBCHEM.toString).toString.toInt)))

      val productResult = enzSummary.get(ReactionKeywords.PRODUCTS.toString).asInstanceOf[BasicDBList].toList.asInstanceOf[List[BasicDBObject]]
      val productMolecules = productResult.flatMap(r =>
        List.fill(r.get(ReactionKeywords.COEFFICIENT.toString).toString.toInt)(chemicalQuery(r.get(ReactionKeywords.PUBCHEM.toString).toString.toInt))
      )

      if (substrateMolecules.forall(_.isDefined) && productMolecules.forall(_.isDefined)){
        Option(MoleculeReaction(reactionId.toString.toInt, substrateMolecules.map(_.get), productMolecules.map(_.get)))
      } else {
        println(reactionId.toString.toInt)
        None
      }
    }).toList

    moleculeMap
  }

  def querySubstrateAndProductInchisByReactionIds(mongoConnection: MongoDB)(reactionIds: List[Long]): List[InchiReaction] ={
    // Now query for chemicals
    val dbReactionIdsIterator = getReactionsById(mongoConnection)(reactionIds)

    val chemicalQuery = getChemicalsInchiById(mongoConnection)_
    val moleculeMap: List[InchiReaction] = dbReactionIdsIterator.toStream.par.flatMap(result => {
      val reactionId = result.get(ReactionKeywords.ID.toString)

      val enzSummary = result.get(ReactionKeywords.ENZ_SUMMARY.toString).asInstanceOf[DBObject]

      val substrateResult = enzSummary.get(ReactionKeywords.SUBSTRATES.toString).asInstanceOf[BasicDBList].toList.asInstanceOf[List[BasicDBObject]]

      val substrateMolecules = substrateResult.flatMap(r =>
        List.fill(r.get(ReactionKeywords.COEFFICIENT.toString).toString.toInt)(chemicalQuery(r.get(ReactionKeywords.PUBCHEM.toString).toString.toInt)))

      val productResult = enzSummary.get(ReactionKeywords.PRODUCTS.toString).asInstanceOf[BasicDBList].toList.asInstanceOf[List[BasicDBObject]]
      val productMolecules = productResult.flatMap(r =>
        List.fill(r.get(ReactionKeywords.COEFFICIENT.toString).toString.toInt)(chemicalQuery(r.get(ReactionKeywords.PUBCHEM.toString).toString.toInt))
      )

      if (substrateMolecules.forall(_.isDefined) && productMolecules.forall(_.isDefined)){
        Option(InchiReaction(reactionId.toString.toInt, substrateMolecules.map(_.get), productMolecules.map(_.get)))
      } else {
        None
      }
    }).toList

    moleculeMap
  }

  def getReactionsById(mongoConnection: MongoDB)(reactionIds: List[Long]): Iterator[DBObject] ={
    val reactionList = new BasicDBList()

    reactionIds.foreach(x => reactionList.add(new BasicDBObject(ChemicalKeywords.ID.toString, x)))

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = defineMongoOr(reactionList)

    // Create the return filter by adding all fields onto
    val reactionReturnFilter = new BasicDBObject()

    val substrateWord = s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}"
    val productWord = s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}"
    reactionReturnFilter.put(substrateWord, true)
    reactionReturnFilter.put(productWord, true)

    // Deploy DB query w/ error checking to ensure we got something
    localLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionReturnFilter")

    val r = mongoQueryReactions(mongoConnection)(reactionIdQuery, reactionReturnFilter)
    localLogger.info("Finished querying by reactionId.")
    r
  }


  case class MoleculeReaction(id: Int, substrates: List[Molecule], products: List[Molecule])
  case class InchiReaction(id: Int, substrates: List[String], products: List[String])
}
