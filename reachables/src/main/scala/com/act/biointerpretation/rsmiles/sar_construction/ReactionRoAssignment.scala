package com.act.biointerpretation.rsmiles.sar_construction

import java.io.{BufferedWriter, File, FileWriter}

import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.l2expansion.L2PredictionCorpus
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.biointerpretation.rsmiles.chemicals.Information.{ChemicalInformation, ReactionInformation}
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object ReactionRoAssignment {
  val LOGGER = LogManager.getLogger(getClass)

  /**
    * Takes in a previous prediction file and a set of reactions.  Then, assigns ROs to reactions that match a prediction.
    *
    * @param predictionDirectory A directory containing one or more prediction files
    * @param reactionsFile       A file containing all the reactions that we want to assign ROs to
    * @param outputFile          File to output the reaction assignments.
    */
  def assignRoToReactions(predictionDirectory: File, reactionsFile: File, outputFile: File)() {
    // All files in this directory will be called prediction files.
    val predictionFiles: List[File] = predictionDirectory.listFiles().filter(_.isFile).toList

    require(predictionFiles.nonEmpty, s"No prediction files found, " +
      s"please check directory ${predictionDirectory.getAbsolutePath} to ensure " +
      s"that prediction files exist at that location.")

    LOGGER.trace("Loading in previous reaction information")
    val reactionInputFile = scala.io.Source.fromFile(reactionsFile)
    val reactions: List[ReactionInformation] = reactionInputFile.getLines().mkString.parseJson.convertTo[List[ReactionInformation]]
    LOGGER.info("Loaded in previous reaction information file.")


    LOGGER.trace(s"Loading all predictions in.  Total of ${predictionFiles.length} prediction files are available.")
    val roPredictions: List[(Int, L2PredictionCorpus)] = predictionFiles.map(file => {

      val corpy = L2PredictionCorpus.readPredictionsFromJsonFile(file)
      val ro = file.getName.toInt

      (ro, corpy)
    })
    LOGGER.info(s"Loaded in ${predictionFiles.length} files for use.  " +
      s"Assigning ROs based on these predictions to reactions now.")

    // Preapply the reactions as they stay constant
    val reactionPredictor: L2PredictionCorpus => List[ReactionInformation] = evaluatePredictionAgainstReactions(reactions) _

    LOGGER.info(s"Assigning ROs to ${reactions.length} reactions that may have a projected reaction from an RO.")
    val assignedRoPredictions: ParSeq[RoAssignments] = roPredictions.par.map(roPrediction => {
      val ro = roPrediction._1

      LOGGER.info(s"Started looking for valid reactions that match ro $ro.")
      val validatedPredictions = reactionPredictor(roPrediction._2)
      LOGGER.info(s"Found ${validatedPredictions.length} matching " +
        s"reaction${if (validatedPredictions.length == 1) "" else "s"} for RO $ro")

      // Return back the RO + validated predictions
      RoAssignments(ro, validatedPredictions)
    })

    LOGGER.info(s"Writing output predictions to file ${outputFile.getAbsolutePath}")
    writeRoAssignmentsToJson(outputFile, assignedRoPredictions.seq.toList)
  }

  /**
    * Substrate and product size agnostic way of assigning ROs to reactions based on projections matching them.
    *
    * @param inputReactions All the reactions in the DB that could be happening
    * @param roPrediction   All the predictions we have for a given RO.
    *
    * @return A list of reactions that are validated by the projection.
    */
  private def evaluatePredictionAgainstReactions(inputReactions: List[ReactionInformation])(roPrediction: L2PredictionCorpus): List[ReactionInformation] = {
    /*
      We do 3 filtering steps here to indicate that a projected reaction matches a db reaction,
      filtering at each step to reduce the number used in the next step.

      1) Check if the substrate and product count is the same
      2) Check if all the substrate strings match
      3) Check if all the product strings match
     */

    // No predictions for this RO
    if (roPrediction.getCorpus.isEmpty) return List()

    /*
      Step 1 - Filter reactions so that they have the same number of substrates and products as the projection.
     */
    val representativePrediction = roPrediction.getCorpus.asScala.head
    val numberOfSubstrateAtoms = representativePrediction.getSubstrateInchis.size
    val numberOfProductAtoms = representativePrediction.getProductInchis.size

    val correctSizeReactions = inputReactions.filter(reaction =>
      (reaction.getSubstrates.length == numberOfSubstrateAtoms) && (reaction.getProducts.length == numberOfProductAtoms))
    /*
      Step 2 - Filter reactions so that all the substrates match a projection
     */
    val predictionSubstrateInchis: List[Set[String]] =
      roPrediction.getCorpus.asScala.map(prediction => {
        val productSet = prediction.getSubstrateInchis.asScala.toSet
        productSet.map(x => MoleculeExporter.exportMolecule(
          MoleculeImporter.importMolecule(x,
            MoleculeFormatType(MoleculeFormat.stdInchi.value,
              List(MoleculeFormat.CleaningOptions.clean2d,
                MoleculeFormat.CleaningOptions.neutralize,
                MoleculeFormat.CleaningOptions.aromatize))
          ),
          MoleculeFormat.stdInchi))
      }).toList

    // Only get reactions with matching input substrates.  We use a set so that the order doesn't matter.
    // This could also, potentially, reduce the number of substrates.
    // To the last point, we've already checked that they are the same; therefore,
    // an inequality in set size reduction means an inequality in set members.
    val validSubstrateReactions = correctSizeReactions.filter(reaction =>
      predictionSubstrateInchis.exists(s => equalSets(s, reaction.getSubstrates)))


    /*
      Step 3 - Filter reactions so that all the products match a projectino
     */
    val predictionProductInchis: List[Set[String]] =
      roPrediction.getCorpus.asScala.map(prediction => prediction.getProductInchis.asScala.toSet).toList

    val reactionsThatMatchThisRo = validSubstrateReactions.filter(
      // For each reaction
      reaction =>
        // There exists a reaction that is fully contained within a predictedProduct set.
        predictionProductInchis.exists(predictedProduct => equalSets(predictedProduct, reaction.getProducts))
    )

    reactionsThatMatchThisRo
  }

  /**
    * Helper method for set equality checks on ChemicalInformation to their chemicals
    *
    * @param elementA A set of elements that should be in the reaction
    * @param elementB The list of chemical informations for a given reaction.
    *
    * @return
    */
  private def equalSets(elementA: Set[String], elementB: List[ChemicalInformation]): Boolean = {
    // Convert to string then create a set out of that to check equality.
    elementA.equals(elementB.map(chemical => chemical.getString).toSet)
  }

  /**
    * Helper method to write the RO assignments to a JSON doc
    *
    * @param outputFile    File to output to
    * @param roAssignments The list of assignments to Jsonify
    */
  def writeRoAssignmentsToJson(outputFile: File, roAssignments: List[RoAssignments]): Unit = {
    val outputWriter = new BufferedWriter(new FileWriter(outputFile))
    outputWriter.write(roAssignments.toJson.prettyPrint)
    outputWriter.close()
  }

  case class RoAssignments(ro: Int, reactions: List[ReactionInformation]) {}

}
