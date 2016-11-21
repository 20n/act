package com.act.biointerpretation.l2expansion.sparkprojectors

import java.io.File

import chemaxon.license.LicenseManager
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.l2expansion.sparkprojectors.utility.{ProjectionResult, ProjectorCliHelper}
import org.apache.commons.cli.{CommandLine, Option => CliOption}
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

trait BasicSparkROProjector extends ProjectorCliHelper {
  /**
    * The most basic SparkROProjector, contains the abstract methods that each actual projector will implement.
    */
  final val OPTION_EXHAUSTIVE = "e"
  final val OPTION_REVERSE = "r"
  final val OPTION_LICENSE_FILE = "l"
  final val OPTION_SPARK_MASTER = "m"
  final val OPTION_VALID_CHEMICAL_TYPE = "v"

  val runningClass: Class[_]

  val HELP_MESSAGE = "A Spark job that will project the set of validation ROs over a group of substrates.  " +
    s"You are currently running the projector version $runningClass"
  protected val DEFAULT_SPARK_MASTER = "spark://spark-master:7077"
  private val LOGGER = LogManager.getLogger(getClass)

  // TODO Careful, this also changes our logger's status.
  private val SPARK_LOG_LEVEL = "INFO"

  // These two methods handle the input of molecules into the projector and the command line args associated with that.
  def getInputMolecules(cli: CommandLine): Stream[Stream[String]]
  def getInputCommandLineOptions: List[CliOption.Builder]

  // These two methods handle the output of projection results and the command line args associated with that.
  def handleTermination(cli: CommandLine)(results: Stream[ProjectionResult])

  def getTerminationCommandLineOptions: List[CliOption.Builder]

  final def main(args: Array[String]): Unit = {
    // Parse CLI options
    val cli = parseCommandLine(args)

    // Handle Input
    val inchiCombinations: Stream[Stream[String]] = getInputMolecules(cli)
    val validInChIs = validateInChIs(inchiCombinations)

    // Handle Projection
    val spark = setupSpark(cli)
    val results: Stream[ProjectionResult] = callProjector(cli)(spark, validInChIs)

    // Output projections
    handleTermination(cli)(results)
  }

  private def validateInChIs(combinations: Stream[Stream[String]]): Stream[Stream[String]] = {
    LOGGER.info("Attempting to filter out combinations with invalid InChIs.  " +
      s"Starting with ${combinations.length} inchis.")
    val validInChIs: Stream[Stream[String]] = combinations.filter(group => {
      try {
        group.foreach(inchi => {
          MoleculeImporter.importMolecule(inchi)
        })
        true
      } catch {
        case e: Exception => false
      }
    })
    LOGGER.info(s"Filtering removed ${combinations.length - validInChIs.length}" +
      s" combinations, ${validInChIs.length} remain.")
    validInChIs
  }

  private def setupSpark(cli: CommandLine): SparkContext = {
    // Spark name and connection
    val sparkName = "" +
      s"${if (isExhaustive(cli)) "Exhaustive " else ""}" +
      s"${if (isReverse(cli)) "Reverse " else ""}" +
      s"${runningClass.getSimpleName.replace("$", "")}"

    // Spark Configurations
    val conf = new SparkConf().
      setAppName(sparkName).
      setMaster(getSparkMaster(cli)).
      set("spark.scheduler.mode", "FAIR")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))

    // Actual spark instance
    val spark = new SparkContext(conf)

    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing Chemaxon license file to spark workers")
    val licenseFile = getChemaxonLicenseFile(cli).getAbsolutePath
    LicenseManager.setLicenseFile(licenseFile)
    spark.addFile(licenseFile)
    spark
  }

  private def getSparkMaster(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_SPARK_MASTER, DEFAULT_SPARK_MASTER)
  }

  private def callProjector(cli: CommandLine)
                           (spark: SparkContext, validInchis: Stream[Stream[String]]): Stream[ProjectionResult] = {
    /* ------ Projection ------- */
    LOGGER.warn(s"Starting execution.  Substrate Groups Count: ${validInchis.length}")
    // PROJECT!  Run ERO projection over all InChIs.
    closedScopeProjection(getChemaxonLicenseFile(cli).getName)(isReverse(cli), isExhaustive(cli))(spark, validInchis)
  }

  /* ----- Ordered methods by the processing that happens in main ------ */

  private def isExhaustive(cli: CommandLine): Boolean = {
    cli.hasOption(OPTION_EXHAUSTIVE)
  }

  private def isReverse(cli: CommandLine): Boolean = {
    cli.hasOption(OPTION_REVERSE)
  }

  private def getChemaxonLicenseFile(cli: CommandLine): File = {
    new File(cli.getOptionValue(OPTION_LICENSE_FILE))
  }

  private def closedScopeProjection(licenseFileName: String)
                                   (reverse: Boolean, exhaustive: Boolean)
                                   (spark: SparkContext, validInchis: Stream[Stream[String]]): Stream[ProjectionResult] = {
    /**
      * We need to set the scope of the projection to a smaller unit so that
      * we don't need to ensure a bunch of other stuff serializes.  We don't do any logging or anything in this
      * function, so that we don't need to serialize things like that.
      */
    val seqMapper: Seq[String] => Stream[ProjectionResult] = i => {
      SparkProjectionInstance.project(licenseFileName)(reverse, exhaustive)(i.toList)
    }

    // No need to launch a bunch of parallel tasks if we have a small InChI list.
    val chunkSize = 100
    val groupSize: Int = (validInchis.length / chunkSize) + 1

    // We shuffle before putting into the RDD as it is likely that large InChIs will be placed next to each other,
    // exacerbating the runtime problems that they can pose.
    val inchiRDD: RDD[Seq[String]] = spark.makeRDD(Random.shuffle(validInchis), groupSize)
    val resultRdd: RDD[ProjectionResult] = inchiRDD.flatMap(seqMapper)
    collectAndPersistRdd(resultRdd)
  }

  private def collectAndPersistRdd(results: RDD[ProjectionResult]): Stream[ProjectionResult] = {
    /* This next part represents us jumping through some hoops (that are possibly on fire) in order to make Spark do
     * the thing we want it to do: project in parallel but stream results back for storage.
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
     * for more context on Spark's laziness.
     */
    val resultCount: Long = results.persist().count()
    LOGGER.warn(s"Projection completed with $resultCount results")
    // Use the to list to collect everything
    // TODO Work out a way so that we can do blocking operations
    // in spark so that it can be done in parallel across the cluster
    val resultingStream = results.toLocalIterator.toList.toStream
    cleanup(results)
    resultingStream
  }

  private def cleanup(resultsRDD: RDD[ProjectionResult]): Unit = {
    LOGGER.info("Cleaning up data.  Should finish soon.")
    resultsRDD.unpersist()
  }

  final def getCommandLineOptions: List[CliOption.Builder] = {
    getMinimumCommandLineArgs ::: getInputCommandLineOptions ::: getTerminationCommandLineOptions
  }

  private def getMinimumCommandLineArgs: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file").
        desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

      // TODO Make this true again
      CliOption.builder(OPTION_VALID_CHEMICAL_TYPE).
        longOpt("valid-chemical-types").
        hasArg.
        desc(
          s"""
             |A molecule string format. Currently valid types are inchi, stdInchi, smiles, and smarts.
             |By default, uses stdInChI which is the format: ${MoleculeFormat.getExportString(MoleculeFormat.stdInchi)}.
             |Possible values are:
             |${MoleculeFormat.listPossibleFormats().mkString("|")}
             |""".stripMargin),

      CliOption.builder(OPTION_SPARK_MASTER).
        hasArg().
        longOpt("spark-master").
        desc("Where to look for the spark master connection. " +
          s"Uses $DEFAULT_SPARK_MASTER by default."),

      CliOption.builder(OPTION_REVERSE).
        longOpt("reverse").
        desc("Flag to reverse all reactions."),

      CliOption.builder(OPTION_EXHAUSTIVE).
        longOpt("exhaustive").
        desc("Flag to indicate that substrates should be reacted until exhaustion, " +
          "meaning all possible reactions occur and are returned.  " +
          "Can be quite expensive for substrates with a large quantity of reaction sites."),

      CliOption.builder(OPTION_HELP).argName("help").desc("Prints this help message").longOpt("help"))
    options
  }

  final protected def combinationList(suppliedInchiLists: Stream[Stream[String]]): Stream[Stream[String]] = {
    /**
      * Small utility function that takes in a streams and creates combinations of members of the multiple streams.
      * For example, with two input streams of InChIs we would construct a new stream containing all the
      * possible streams of size 2, where one element comes from each initial stream.
      */
    if (suppliedInchiLists.isEmpty) Stream(Stream.empty)
    else suppliedInchiLists.head.flatMap(i => combinationList(suppliedInchiLists.tail).map(i #:: _))
  }
}
