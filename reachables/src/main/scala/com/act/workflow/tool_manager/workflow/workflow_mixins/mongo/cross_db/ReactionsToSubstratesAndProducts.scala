package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import act.shared.Reaction
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicals
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object ReactionsToSubstratesAndProducts extends MongoWorkflowUtilities {
  private val LOGGER = LogManager.getLogger(getClass)
  /**
    * Provides an easier converter from Inchi => Molecule such that the returned reactions is all in the Molecule format.
    */
  def querySubstrateAndProductMoleculesByReactionIds(mongoConnection: MongoDB)
                                                    (reactionIds: List[Long]): List[Option[MoleculeReaction]] = {
    querySubstrateAndProductInchisByReactionIds(mongoConnection)(reactionIds).map(elem => {
      if (elem.isDefined) {
        Option(inchiReactionToMoleculeReaction(elem.get))
      } else {
        None
      }
    })
  }

  /**
    * Provides a list of reactions that may have been correctly loaded,
    * where each reaction is either None or (Id, Substrate Inchis, Product Inchis).
    * If more than one of a given inchi is found (Coefficient > 1) it is added twice to the reaction.
    */
  def querySubstrateAndProductInchisByReactionIds(mongoConnection: MongoDB)
                                                 (reactionIds: List[Long]): List[Option[InchiReaction]] = {
    // Now query for chemicals
    val dbReactionIdsIterator: Option[Iterator[Reaction]] = getReactionsById(mongoConnection)(reactionIds)

    if (dbReactionIdsIterator.isEmpty) {
      return List()
    }

    createReactions(mongoConnection)(dbReactionIdsIterator.get)
  }

  def querySubstrateAndProductInchis(mongoConnection: MongoDB): List[Option[InchiReaction]] = {
    createReactions(mongoConnection)(mongoConnection.getReactionsIterator)
  }

  private def createReactions(mongoConnection: MongoDB)(iter: Iterator[Reaction]): List[Option[InchiReaction]] ={
    val count = new AtomicInteger()
    val moleculeMap: List[Option[InchiReaction]] = iter.map(result => {
      val reactionId = result.getUUID

      val chemicals = (result.getSubstrates.toList ::: result.getProducts.toList).distinct.map(x => x.toLong)
      val thisReactionsChemicals: Map[Long, Option[String]] = QueryChemicals.getChemicalStringsByIds(mongoConnection)(chemicals)

      val substrateMolecules: List[Option[String]] = result.getSubstrates.toList.flatMap(substrateId => {
        val s: Option[String] = thisReactionsChemicals(substrateId)
        val coefficient: Option[Integer] = Option(result.getSubstrateCoefficient(substrateId))
        List.fill(coefficient.getOrElse[Integer](Integer.valueOf(1)))(s)
      })

      val productMolecules: List[Option[String]] = result.getProducts.toList.flatMap(productId => {
        val s: Option[String] = thisReactionsChemicals(productId)
        val coefficient: Option[Integer] = Option(result.getProductCoefficient(productId))
        List.fill(coefficient.getOrElse[Integer](Integer.valueOf(1)))(s)
      })

      if (count.incrementAndGet() % 1000 == 0){
        LOGGER.info(s"Parsed ${count.get()} reactions so far.")
      }

      // This drops FAKE and Abstract InChIs.
      if (substrateMolecules.forall(_.isDefined) && productMolecules.forall(_.isDefined)) {
        Option(InchiReaction(reactionId.toString.toInt, substrateMolecules.map(_.get), productMolecules.map(_.get)))
      } else {
        None
      }
    }).toList

    moleculeMap
  }

  /**
    * Gets an iterator over all reactions in the database matching the supplied IDs
    */
  def getReactionsById(mongoConnection: MongoDB)(reactionIds: List[Long]): Option[Iterator[Reaction]] = {
    val maybeIterator =
      Option(mongoConnection.getReactionsIteratorById(reactionIds.map(java.lang.Long.valueOf).asJava, true))

    if (maybeIterator.isEmpty) {
      return None
    }
    Option(maybeIterator.get.toIterator)
  }

  implicit def inchiReactionToMoleculeReaction(inchiReaction: InchiReaction): MoleculeReaction = {
    MoleculeReaction(inchiReaction.id, inchiReaction.substrates, inchiReaction.products)
  }

  implicit def stringToMoleculeList(molecules: List[String]): List[Molecule] = {
    molecules.map(MoleculeImporter.importMolecule)
  }

  case class MoleculeReaction(id: Int, substrates: List[Molecule], products: List[Molecule])

  case class InchiReaction(id: Int, substrates: List[String], products: List[String])
}
