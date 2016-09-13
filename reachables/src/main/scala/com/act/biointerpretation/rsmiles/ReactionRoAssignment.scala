package com.act.biointerpretation.rsmiles

import java.io.{BufferedWriter, File, FileWriter}

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

import com.act.biointerpretation.l2expansion.L2PredictionCorpus
import com.act.biointerpretation.rsmiles.AbstractChemicals.ChemicalInformation
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation
import org.apache.log4j.LogManager
import spray.json._
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._


object ReactionRoAssignment {
  val LOGGER = LogManager.getLogger(getClass)

  def assignRoToReactions(predictionDirectory: File, reactionsFile: File, outputFile: File)() {
//    val predictionFiles: List[File] = new File("/Volumes/shared-data/Michael/Rsmiles/TTT/ProjectionResults/").listFiles().filter(_.isFile).toList
//    val reactionsFile = new File("/Volumes/shared-data/Michael/Rsmiles/TTT/FromDatabasemarvin.AbstractReactions1.txt")
//    val outputFile = new File("/Users/michaellampe/Desktop/outputpredictions.json")

    // All files in this directory will be called prediction files.
    val predictionFiles: List[File] = predictionDirectory.listFiles().filter(_.isFile).toList


    LOGGER.info("Loading in previous reaction information")
    val reactionInputFile = scala.io.Source.fromFile(reactionsFile)
    val reactions: List[ReactionInformation] = reactionInputFile.getLines().mkString.parseJson.convertTo[List[ReactionInformation]]


    LOGGER.info(s"Loading all predictions in.  Total of ${predictionFiles.length} prediction files are available.")
    val roPredictions: List[(Int, L2PredictionCorpus)] = predictionFiles.map(file => {

      val corpy = L2PredictionCorpus.readPredictionsFromJsonFile(file)
      val ro = file.getName.toInt

      (ro, corpy)
    })

    // Preapply the reactions
    val reactionPredictor: L2PredictionCorpus => List[ReactionInformation] = evaluatePredictionAgainstReactions(reactions)_

    LOGGER.info(s"Assigning ROs to ${reactions.length} reactions that may have both the substrate and product of a projected RO.")

    val assignedRoPredictions: ParSeq[RoAssignments] = roPredictions.par.map(roPrediction => {
      val validatedPredictions = reactionPredictor(roPrediction._2)
      LOGGER.info(s"Found ${validatedPredictions.length} matching " +
        s"reaction${if (validatedPredictions.length == 1) "" else "s"} for RO ${roPrediction._1}")

      // Return back the RO + validated predictions
      RoAssignments(roPrediction._1, validatedPredictions)
    })

    LOGGER.info(s"Writing output predictions to file ${outputFile.getAbsolutePath}")
    writeRoAssignmentsToJson(outputFile, assignedRoPredictions.seq.toList)
  }

  def evaluatePredictionAgainstReactions(inputReactions: List[ReactionInformation])(roPrediction: L2PredictionCorpus): List[ReactionInformation] = {
    /*
      We do 3 filtering steps here.
      1) Check if the substrate and product count is the same
      2) Check if all the substrate strings match
      3) Check if all the product strings match
     */


    /*
      Step 1 - Filter reactions so that they have the same number of substrates and products as the projection.
     */

    // No predictions for this RO
    if (roPrediction.getCorpus.isEmpty) return List()

    val representativePrediction = roPrediction.getCorpus.asScala.head
    val numberOfSubstrateAtoms = representativePrediction.getSubstrateInchis.size
    val numberOfProductAtoms = representativePrediction.getProductInchis.size

    val correctSizeReactions = inputReactions.filter(reaction =>
      (reaction.getSubstrates.length == numberOfSubstrateAtoms) && (reaction.getProducts.length == numberOfProductAtoms)
    )
    // Breakout if no more work to be done
    if (correctSizeReactions.isEmpty) return List()


    /*
      Step 2 - Filter reactions so that all the substrates match a projection
     */
    val predictionSubstrateInchis: List[Set[String]] =
      roPrediction.getCorpus.asScala.map(prediction => prediction.getSubstrateInchis.asScala.toSet).toList

    // Only get reactions with matching input substrates.  We use a set so that order doesn't matter.
    val validSubstrateReactions = correctSizeReactions.filter(reaction =>
      predictionSubstrateInchis.exists(s => equalSets(s, reaction.getSubstrates)))

    // Breakout if no more work to be done
    if (validSubstrateReactions.isEmpty) return List()


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

    if (reactionsThatMatchThisRo.isEmpty) return List()

    reactionsThatMatchThisRo
  }

  def equalSets(elementA: Set[String], elementB: List[ChemicalInformation]): Boolean = {
    // Convert to string then create a set out of that to check equality.
    elementA.equals(elementB.map(chemical => chemical.getString).toSet)
  }

  def writeRoAssignmentsToJson(outputFile: File, roAssignments: List[RoAssignments]): Unit = {
    val outputWriter = new BufferedWriter(new FileWriter(outputFile))
    outputWriter.write(roAssignments.toJson.prettyPrint)
    outputWriter.close()
  }

  case class RoAssignments(ro: Int, reactions: List[ReactionInformation]) {}
}
