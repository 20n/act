package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import act.shared.Reaction
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicals
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

trait ReactionsToSubstratesAndProducts extends MongoWorkflowUtilities with QueryChemicals {
  def querySubstrateAndProductMoleculesByReactionIds(mongoConnection: MongoDB)(reactionIds: List[Long]): List[Option[MoleculeReaction]] ={
    querySubstrateAndProductInchisByReactionIds(mongoConnection)(reactionIds).map(elem => {
      if (elem.isDefined){
        Option(inchiToMolecule(elem.get))
      } else {
        None
      }
    })
  }

  def querySubstrateAndProductInchisByReactionIds(mongoConnection: MongoDB)(reactionIds: List[Long]): List[Option[InchiReaction]] ={
    // Now query for chemicals
    val dbReactionIdsIterator: Option[Iterator[Reaction]] = getReactionsById(mongoConnection)(reactionIds)

    if (dbReactionIdsIterator.isEmpty){
      return List()
    }

    val chemicalQuery = getChemicalsStringById(mongoConnection)_
    val moleculeMap: List[Option[InchiReaction]] = dbReactionIdsIterator.get.toStream.map(result => {
      val reactionId = result.getUUID

      val enzSummary = result

      val substrateMolecules: List[Option[String]] = result.getSubstrates.toList.flatMap(substrateId => {
        List.fill(result.getSubstrateCoefficient(substrateId))(chemicalQuery(substrateId, MoleculeFormat.inchi))
      })

      val productMolecules: List[Option[String]] = result.getProducts.toList.flatMap(productId => {
        List.fill(result.getProductCoefficient(productId))(chemicalQuery(productId, MoleculeFormat.inchi))
      })

      if (substrateMolecules.forall(_.isDefined) && productMolecules.forall(_.isDefined)){
        Option(InchiReaction(reactionId.toString.toInt, substrateMolecules.map(_.get), productMolecules.map(_.get)))
      } else {
        None
      }
    }).toList

    moleculeMap
  }

  def getReactionsById(mongoConnection: MongoDB)(reactionIds: List[Long]): Option[Iterator[Reaction]] ={
    val maybeIterator = Option(mongoConnection.getReactionsIteratorById(reactionIds.map(java.lang.Long.valueOf).asJava, true))
    if (maybeIterator.isEmpty){
      return None
    }
    Option(maybeIterator.get.toIterator)
  }


  case class MoleculeReaction(id: Int, substrates: List[Molecule], products: List[Molecule])
  implicit def inchiToMolecule(inchiReaction: InchiReaction): MoleculeReaction ={
    MoleculeReaction(inchiReaction.id, inchiReaction.substrates, inchiReaction.products)
  }
  implicit def stringToMoleculeList(molecules: List[String]): List[Molecule] = {
    molecules.map(MoleculeImporter.importMolecule)
  }
  case class InchiReaction(id: Int, substrates: List[String], products: List[String])
}
