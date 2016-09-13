package com.act.biointerpretation.rsmiles

import java.io.File

import com.act.biointerpretation.l2expansion.L2PredictionCorpus
import com.act.biointerpretation.rsmiles.AbstractChemicals.ChemicalInformation
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation
import org.apache.log4j.LogManager
import spray.json._
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object AssignRoToReactions {
  val LOGGER = LogManager.getLogger(getClass)
  def main(args: Array[String]) {
    //val chemaxonLicenseFile: String = args(0)

    val predictionFiles: List[File] = new File("/Volumes/shared-data/Michael/Rsmiles/TTT/ProjectionResults/").listFiles().filter(_.isFile).toList
    val reactionsFile = new File("/Volumes/shared-data/Michael/Rsmiles/TTT/FromDatabasemarvin.AbstractReactions1.txt")

    //val predictionFiles: List[File] = new File(args(1)).listFiles.filter(_.isFile).toList
    //val reactionsFile: File = new File(args(2))


    //chemaxonLicenseFile: String)(predictionFiles: List[File])(reactionsFile: File

    val reactionInputFile = scala.io.Source.fromFile(reactionsFile)
    val reactions: List[ReactionInformation] = reactionInputFile.getLines().mkString.parseJson.convertTo[List[ReactionInformation]]


    val roPredictions: List[(Int, L2PredictionCorpus)] = predictionFiles.map(file => {

      val corpy = L2PredictionCorpus.readPredictionsFromJsonFile(file)
      val ro = file.getName.toInt

      (ro, corpy)
    })

    // Prefilter so only matching substrates are looked at
    val possibleReactions: ParSeq[(Int, List[ReactionInformation])]= roPredictions.par.map(roPrediction => {
      // Multiple predictions for a given RO
      val substrateInchis: List[Set[String]] = roPrediction._2.getCorpus.asScala.map(prediction => prediction.getSubstrateInchis.asScala.toSet).toList


      // Only get reactions with matching substrates.  Each substrate => the same nubmer
      val validSubstrateReactions = reactions.filter(reaction => substrateInchis.exists(s => equalSets(s, reaction.getSubstrates)))

      if (validSubstrateReactions.isEmpty) {
        (roPrediction._1, List())
      }
      else {
        // Do same thing with products
        val productInchis: List[Set[String]] =
          roPrediction._2.getCorpus.asScala.map(prediction => prediction.getProductInchis.asScala.toSet).toList

        val reactionsThatMatchThisRo = validSubstrateReactions.filter(
          // For each reaction
          reaction =>
            // There exists a reaction that is fully contained within a predictedProduct set.
            productInchis.exists(predictedProduct => containsAllElements(predictedProduct, reaction.getProducts))
        )
        LOGGER.info(s"Found ${reactionsThatMatchThisRo.length} matching reaction ${roPredictions._1}")
        (roPrediction._1, reactionsThatMatchThisRo)
      }
    })

    println(possibleReactions)
  }

  def containsAllElements(elementA: Set[String], elementB: List[ChemicalInformation]): Boolean = {
    // Forall will return true if elements are empty, but that isn't what we want.
    if (elementB.isEmpty) return false
    // Ensure that all of chemicalB is in a single elementA
    elementB.forall(chemical => elementA(chemical.getString))
  }

  def equalSets(elementA: Set[String], elementB: List[ChemicalInformation]): Boolean = {
    // Conver to string then create a set out of that to check equality.
    elementA.equals(elementB.map(chemical => chemical.getString).toSet)
  }
}