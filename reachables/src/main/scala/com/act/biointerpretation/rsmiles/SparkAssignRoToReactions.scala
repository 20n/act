package com.act.biointerpretation.rsmiles

import java.io.File

import chemaxon.license.LicenseManager
import chemaxon.struc.Molecule

import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.l2expansion.{AllPredictionsGenerator, L2PredictionCorpus, SingleSubstrateRoExpander}

import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation

import org.apache.log4j.LogManager

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import org.joda.time.{DateTime, DateTimeZone}

object SparkAssignRoToReactions {
  private val SPARK_LOG_LEVEL = "WARN"

  val LOGGER = LogManager.getLogger(getClass)

  def assignRos(reactions: List[ReactionInformation], projections: List[L2PredictionCorpus], chemaxonLicenseFile: String) {
    LOGGER.info(s"Validating license file at $chemaxonLicenseFile")
    LicenseManager.setLicenseFile(chemaxonLicenseFile)

    // Don't set a master here, spark-submit will do that for us.
    val conf = new SparkConf().setAppName("Spark RO Reaction Assignment")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    // Silence Spark's verbose logging, which can make it difficult to find our own log messages.
    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing license file to spark workers")
    spark.addFile(chemaxonLicenseFile)
    val licenseFileName = new File(chemaxonLicenseFile).getName

    LOGGER.info("Building ERO RDD")
    val eroRDD: RDD[ReactionInformation] = spark.makeRDD(reactions, reactions.size)

    val resultsRDD: RDD[(ReactionInformation, Option[Int])] =
      eroRDD.map(reaction => {
        val roResult = compute.run(licenseFileName, reaction, projections)
        (reaction, roResult)
      })
  }
}

object compute {
  private val LOGGER = LogManager.getLogger(getClass)

  def run(licenseFileName: String, reaction: ReactionInformation, predictions: List[L2PredictionCorpus]): Option[Int] = {
    val startTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val localLicenseFile = SparkFiles.get(licenseFileName)

    LOGGER.info(s"Using license file at $localLicenseFile (file exists: ${new File(localLicenseFile).exists()})")
    LicenseManager.setLicenseFile(localLicenseFile)

//    predictions.par.flatMap(predictionRo => {
//      predictionRo.getPredictionFromId()
//    })

    None
  }
}
