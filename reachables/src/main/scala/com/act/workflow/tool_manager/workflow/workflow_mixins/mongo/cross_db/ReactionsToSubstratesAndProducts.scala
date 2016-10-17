package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicalInchi
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

trait ReactionsToSubstratesAndProducts extends MongoWorkflowUtilities with QueryChemicalInchi {

  def querySubstratesAndProductsByReactionIds(mongoConnection: MongoDB)(reactionIds: List[Long]): Unit ={
    val methodLogger = LogManager.getLogger("querySubstratesAndProductsByReactionIds")

    /*
      Query Database for Reaction IDs based on a given RO

      Map RO values to a list of mechanistic validator things we will want to see
    */

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
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection)(reactionIdQuery, reactionReturnFilter)

    // Now query for chemicals

    val chemicalQuery = getMoleculeById(mongoConnection)_

    val moleculeMap: List[ChemicalReaction] = dbReactionIdsIterator.flatMap(result => {
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
        Option(ChemicalReaction(reactionId.toString.toInt, substrateMolecules.map(_.get), productMolecules.map(_.get)))
      } else {
        None
      }
    }).toList

    println(moleculeMap.length)

  }

  case class ChemicalReaction(id: Int, substrates: List[Molecule], products: List[Molecule])

}
