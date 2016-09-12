package com.act.biointerpretation.rsmiles

import java.io.{BufferedWriter, File, FileWriter}

import chemaxon.license.LicenseManager
import com.act.biointerpretation.l2expansion.L2PredictionCorpus
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import org.joda.time.{DateTime, DateTimeZone}
import spray.json._

object SparkAssignRoToReactions {
  val LOGGER = LogManager.getLogger(getClass)
  private val SPARK_LOG_LEVEL = "WARN"

  def main(args: Array[String]) {
    val chemaxonLicenseFile: String = args(0)
    val predictionFiles: List[File] = new File(args(1)).listFiles.filter(_.isFile).toList
    val reactionsFile: File = new File(args(2))


    //chemaxonLicenseFile: String)(predictionFiles: List[File])(reactionsFile: File

    val reactionInputFile = scala.io.Source.fromFile(reactionsFile)
    val reactions = reactionInputFile.getLines().mkString.parseJson.convertTo[List[ReactionInformation]]


    val predictions: List[(Int, L2PredictionCorpus)] = predictionFiles.map(file => {

      val corpy = L2PredictionCorpus.readPredictionsFromJsonFile(file)
      val ro = file.getName.toInt

      (ro, corpy)
    })


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

    val computeWithLicense = compute.run(licenseFileName) _
    val resultsRDD: RDD[(ReactionInformation, List[Int])] =
      eroRDD.map(reaction => {
        val roResult = computeWithLicense(reaction, predictions)
        (reaction, roResult)
      })


    /* This next part represents us jumping through some hoops (that are possibly on fire) in order to make Spark do
     * the thing we want it to do: project in parallel but stream results back for storage partitioned by RO.
     *
     * All operations on RDDs are performed lazily.  Only operations that require some data to be returned to the driver
     * process will initiate the application of those RDD operations on the cluster.  That is, functions like `count()`
     * and `collect()` initiate the evaluation of map() on an RDD.
     * For this job, we'd like Spark to project all of the single substrate RDDs in parallel, and then send the results
     * back to the driver so that we can write those projections out into files on the local machine.  Unfortunately,
     * `collect()` will wait for and then load into memory *all* of the contents of an RDD.  If we use a chain of calls
     * like `rdd.map.collect()`, Spark will compute the projections in parallel but we'll run out of memory before we're
     * able to manifest and store those projections.
     *
     * Spark does allow us to iterate over work units (partitions) of an RDD one at a time using `toLocalIterator()`.
     * Using `toLocalIterator()`, we can slurp in and write out one partition at a time, which uses *much* less memory.
     * However, thanks again to laziness, the partitions will only be evaluated as the driver asks to read them.  This
     * puts the job into a mode where the projections are done on the cluster's work nodes, but they're run serially
     * as `toLocalIterator()` requests them.  Yikes.
     *
     * To work around this mess, we start the job by running an aggregation (`count()`) on the RDD to force projection
     * evaluation in parallel.  We chain that call with a `persist()` call to make sure Spark knows we're going to
     * do something else with the resultsRDD after the `count()` call is complete--if we don't `persist()`, Spark will
     * likely try to recompute the whole thing when we iterate over the partitions.  We call `unpersist()` at the end to
     * tell Spark that we're done with the RDD and the memory it consumes can be reclaimed.
     *
     * Thus our workflow amounts to:
     *   rdd.map(doSomeWork).persist().count()
     *   rdd.toLocalIterator(writeOutTheRDD)
     *   rdd.unpersist()
     *
     * This is clunky and perhaps ugly, but effective.  The projection is done in parallel while the driver is able to
     * stream the results back a piece at a time.  The streaming adds a few minutes to the total runtime of the job, but
     * it's a small price to pay for reducing the driver's memory consumption to a fraction of what it would be if we
     * had to call `collect()`.
     *
     * See http://stackoverflow.com/questions/31383904/how-can-i-force-spark-to-execute-code/31384084#31384084
     * for more context on Spark's laziness. */
    val resultCount = resultsRDD.persist().count()
    LOGGER.info(s"RO matching completed with $resultCount results")

    // Write RO matches to a file.
    val reactionIdToRoPairs: List[(Int, List[Int])] = resultsRDD.toLocalIterator.map({ case (reactionInfo, ros) => {
      (reactionInfo.reactionId, ros)
    }
    }).toList

    val outputDir = "/Volumes/shared-data/Michael/Rsmiles/RoAssignments/"
    val outputFile = new BufferedWriter(new FileWriter(new File(outputDir, "OneSubstrate")))

    val assignedRos: List[RoAssignments] = reactionIdToRoPairs.map({ case (rxnId, matchingRos) => {
      RoAssignments(rxnId, matchingRos)
    }
    })

    outputFile.write(assignedRos.toJson.prettyPrint)
    outputFile.close()

    // Release the RDD now that we're done reading it.
    resultsRDD.unpersist()
  }

  case class RoAssignments(rxnId: Int, ros: List[Int]) {}
}

object compute {
  private val LOGGER = LogManager.getLogger(getClass)

  def run(licenseFileName: String)(reaction: ReactionInformation, predictions: List[(Int, L2PredictionCorpus)]): List[Int] = {
    val startTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val localLicenseFile = SparkFiles.get(licenseFileName)

    LOGGER.info(s"Using license file at $localLicenseFile (file exists: ${new File(localLicenseFile).exists()})")
    LicenseManager.setLicenseFile(localLicenseFile)

    val substrateInchis = reaction.getSubstrates
    val productInchis = reaction.getProducts

    val matchingRos: List[Int] = predictions.flatMap(
      { case (ro, prediction) =>
        // TODO Make this valid for more than 1 substrate
        val containsSubstrate = prediction.getUniqueSubstrateInchis.contains(substrateInchis)
        val containsProduct = prediction.getUniqueProductInchis.contains(productInchis)

        if (containsSubstrate && containsProduct) {
          Some(ro)
        } else {
          None
        }
      }
    )
    LOGGER.info("Valids ROs were" + matchingRos)

    matchingRos
  }
}

