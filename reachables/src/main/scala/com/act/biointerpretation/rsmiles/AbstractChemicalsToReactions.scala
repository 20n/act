package com.act.biointerpretation.rsmiles

import java.io.File

import act.server.MongoDB
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.MoleculeExporter
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.mechanisminspection.Ero
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import org.apache.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.parallel.immutable.ParRange


object AbstractChemicalsToReactions {
  val logger = LogManager.getLogger(getClass)

  def main(args: Array[String]) {
    val db = Mongo.connectToMongoDatabase()
    val abstractChemicals = AbstractChemicals.getAbstractChemicals(db)
    ParRange(1, 6, step = 1, inclusive = true).foreach(subCount => {
      val abstractReactions = AbstractReactions.getAbstractReactions(db)(abstractChemicals, subCount)

      writeSubstrateStringsForSubstrateCount(db)(abstractReactions.seq.toList,
        new File("/Volumes/shared-data/Michael/Rsmiles", s"AbstractReactions$subCount.Substrates"))
    })
  }

  // Keeps track of other concurrent jobs that have previously determined the number of substrates in a reaction.

  def writeSubstrateStringsForSubstrateCount(mongoDb: MongoDB)(reactions: List[ReactionInformation], outputFile: File): Unit = {
    require(!outputFile.isDirectory, "The file you designated to output your files " +
      "to is a directory and therefore is not a valid path.")
    val substrates: Seq[String] = reactions.flatMap(_.getSubstrates.map(_.getString)).seq
    new L2InchiCorpus(substrates).writeToFile(outputFile)
  }

  def projectReactionToDetermineRo(projector: ReactionProjector, eros: List[Ero])(reactionInformation: ReactionInformation): Option[Int] = {
    val project = projectionArray(projector, reactionInformation.getSubstrates.map(_.getMolecule)) _

    // Full projection for RO
    val projectedProducts: List[(Int, Molecule)] = eros.flatMap(ro => project(ro)).flatten

    for (products <- reactionInformation.getProducts) {
      val productSmart = MoleculeExporter.exportAsSmarts(products.getMolecule)
      for ((ro, molecule) <- projectedProducts) {
        val moleculeSmart = MoleculeExporter.exportAsSmarts(molecule)
        if (productSmart.equals(moleculeSmart)) {
          return Option(ro)
        }
      }
    }

    None
  }

  private def projectionArray(projector: ReactionProjector, substrates: List[Molecule])(ro: Ero): Option[List[(Int, Molecule)]] = {
    val projection = projector.getAllProjectedProductSets(substrates.toArray, ro.getReactor).toList.flatten
    if (projection.nonEmpty) {
      return Option(List.fill(projection.length)(ro.getId.intValue()) zip projection)
    }

    None
  }

  object Mongo extends MongoWorkflowUtilities {}
}
