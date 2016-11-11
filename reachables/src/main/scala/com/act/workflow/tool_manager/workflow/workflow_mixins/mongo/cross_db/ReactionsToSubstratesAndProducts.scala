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
  private val defaultReactionChemicalCoefficient = 1
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

    createReactions(mongoConnection)(dbReactionIdsIterator.get).toList
  }

  def querySubstrateAndProductInchis(mongoConnection: MongoDB): List[Option[InchiReaction]] = {
    createReactions(mongoConnection)(mongoConnection.getReactionsIterator).toList
  }

  def querySubstrateAndProductInchisIterator(mongoConnection: MongoDB): Iterator[Option[InchiReaction]] = {
    createReactions(mongoConnection)(mongoConnection.getReactionsIterator)
  }

  def querySubstrateAndProductInchisJava(mongoConnection: MongoDB): java.util.List[InchiReaction] = {
    createReactions(mongoConnection)(mongoConnection.getReactionsIterator).toList.flatten.asJava
  }

  def querySubstrateAndProductInchisJavaIterator(mongoConnection: MongoDB): java.util.Iterator[InchiReaction] = {
    createReactions(mongoConnection)(mongoConnection.getReactionsIterator).flatten.asJava
  }

  private def createReactions(mongoConnection: MongoDB)(iter: Iterator[Reaction]): Iterator[Option[InchiReaction]] = {
    def chemicalIdToStrings(chemicalStringCache: Map[Long, Option[String]])
                           (substrateId: Long, rawCoefficient: Option[Integer]): List[Option[String]] ={
      val chemicalString: Option[String] = chemicalStringCache(substrateId)
      // By default we say chemicals without coefficients have a coefficient of 1
      val coefficient = rawCoefficient.getOrElse[Integer](Integer.valueOf(defaultReactionChemicalCoefficient))
      // Creates a list that has the chemical string repeated by the coefficient of that chemical string.
      List.fill(coefficient)(chemicalString)
    }

    val count = new AtomicInteger()
    iter.map(result => {
      val reactionId = result.getUUID

      // Compute all the substrates and product strings for this reaction in one query
      val chemicals = (result.getSubstrates.toList ::: result.getProducts.toList).distinct.map(x => x.toLong)
      val thisReactionsChemicals: Map[Long, Option[String]] = QueryChemicals.getChemicalStringsByIds(mongoConnection)(chemicals)

      // We create a mapper which uses the current cache to map a chemicalId and Coefficient
      // to a list of one or more strings.
      val chemicalMapper: (Long, Option[Integer]) => List[Option[String]] = chemicalIdToStrings(thisReactionsChemicals)

      // Substrates first
      val substrateMolecules: List[Option[String]] = result.getSubstrates.toList.flatMap(substrateId => {
        val rawCoefficient: Option[Integer] = Option(result.getSubstrateCoefficient(substrateId))
        chemicalMapper(substrateId, rawCoefficient)
      })

      // Then products
      val productMolecules: List[Option[String]] = result.getProducts.toList.flatMap(productId => {
        val rawCoefficient: Option[Integer] = Option(result.getProductCoefficient(productId))
        chemicalMapper(productId, rawCoefficient)
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
    })
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
