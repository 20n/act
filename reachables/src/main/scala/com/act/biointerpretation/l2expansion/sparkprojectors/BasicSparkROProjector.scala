package com.act.biointerpretation.l2expansion.sparkprojectors

import java.io.File

import chemaxon.license.LicenseManager
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

// Basic storage class for serializing and deserializing projection results
case class ProjectionResult(substrates: List[String], ros: String, products: List[String]) extends Serializable

protected trait ProjectorCliHelper {
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE: String
  /**
    * A class full of a few command line helpers for SparkRoProjectors
    */
  private val LOGGER = LogManager.getLogger(getClass)
  HELP_FORMATTER.setWidth(100)

  final def checkLicenseFile(licenseFile: String): File = {
    LOGGER.info(s"Validating license file at $licenseFile")
    LicenseManager.setLicenseFile(licenseFile)
    new File(licenseFile)
  }

  final def createOutputDirectory(directory: File): Unit = {
    if (directory.exists() && !directory.isDirectory) {
      LOGGER.error(s"Found output directory at ${directory.getAbsolutePath} but is not a directory")
      exitWithHelp(getCommandLineOptions)
    } else {
      LOGGER.info(s"Creating output directory at ${directory.getAbsolutePath}")
      directory.mkdirs()
    }
  }

  final def parse(opts: Options, args: Array[String]): CommandLine ={
    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        LOGGER.error(s"Argument parsing failed: ${e.getMessage}\n")
        exitWithHelp(opts)
    }

    if (cl.isEmpty) {
      LOGGER.error("Detected that command line parser failed to be constructed.")
      exitWithHelp(opts)
    }

    if (cl.get.hasOption("help")) exitWithHelp(opts)

    cl.get
  }

  private def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  def getCommandLineOptions: Options
}

trait BasicSparkROProjector extends ProjectorCliHelper {
  /**
    * The most basic SparkROProjector, contains the abstract methods that each actual projector will implement.
    */
  final val OPTION_EXHAUSTIVE = "e"
  final val OPTION_REVERSE = "r"
  final val OPTION_LICENSE_FILE = "l"
  final val OPTION_SPARK_MASTER = "m"
  final val OPTION_VALID_CHEMICAL_TYPE = "v"
  final val OPTION_HELP = "h"

  val runningClass: Class[_]
  val HELP_MESSAGE = "A Spark job that will project the set of validation ROs over a group of substrates.  " +
    s"You are currently running the projector version $runningClass"
  protected val DEFAULT_SPARK_MASTER = "spark://spark-master:7077"
  private val LOGGER = LogManager.getLogger(getClass)
  private val SPARK_LOG_LEVEL = "WARN"

  // Basic methods that implementing objects utilize.
  def getValidInchiCommandLineOptions: List[CliOption.Builder]
  def getTerminationCommandLineOptions: List[CliOption.Builder]

  def handleTermination(cli: CommandLine)(results: Stream[ProjectionResult])

  def getInChIGroups(cli: CommandLine): Stream[Stream[String]]

  final def combinationList(suppliedInchiLists: Stream[Stream[String]]): Stream[Stream[String]] = {
    if (suppliedInchiLists.isEmpty) Stream(Stream.empty)
    else suppliedInchiLists.head.flatMap(i => combinationList(suppliedInchiLists.tail).map(i #:: _))
  }

  final def main(args: Array[String]): Unit = {
    val cli = parseCommandLineOptions(args)
    // Get valid InChIs
    val inchiCombinations: Stream[Stream[String]] = getInChIGroups(cli)
    val validInChIs = validateInChIs(inchiCombinations)
    val spark = setupSpark(cli)

    val resultsRDD: RDD[ProjectionResult] = callProjector(cli)(spark, validInChIs)
    val collectedResults: Stream[ProjectionResult] = collectAndPersistRdd(resultsRDD)

    handleTermination(cli)(collectedResults)
    cleanup(resultsRDD)
  }

  private def parseCommandLineOptions(args: Array[String]): CommandLine = {
    parse(getCommandLineOptions, args)
  }

  final def getCommandLineOptions: Options = {
    val optionsList = getMinimumCommandLineArgs ::: getValidInchiCommandLineOptions ::: getTerminationCommandLineOptions
    val opts: Options = new Options()
    for (opt <- optionsList) {
      opts.addOption(opt.build)
    }
    opts
  }

  private def getMinimumCommandLineArgs: List[CliOption.Builder] ={
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file").
        desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

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

  /* ----- Ordered methods by the processing that happens in main ------ */

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

  private def setupSpark(cli: CommandLine): SparkContext ={
    /* --------- Setup Spark ---------- */
    // Spark name and connection
    val sparkName = "" +
      s"${if (isExhaustive(cli)) "Exhaustive " else ""}" +
      s"${if (isReverse(cli)) "Reverse " else ""}" +
      s"${runningClass.getSimpleName.replace("$", "")}"

    val sparkMaster = getSparkMaster(cli)
    val conf = new SparkConf().setAppName(sparkName).setMaster(sparkMaster)

    // Spark Configurations
    conf.set("spark.scheduler.mode", "FAIR")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))

    // Actual spark instance
    val spark = new SparkContext(conf)
    spark.setLogLevel(SPARK_LOG_LEVEL)
    LOGGER.info("Distributing Chemaxon license file to spark workers")

    spark.addFile(getChemaxonLicenseFile(cli).getAbsolutePath)
    spark
  }

  private def getSparkMaster(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_SPARK_MASTER, DEFAULT_SPARK_MASTER)
  }

  private def isExhaustive(cli: CommandLine): Boolean = {
    cli.hasOption(OPTION_EXHAUSTIVE)
  }

  private def isReverse(cli: CommandLine): Boolean = {
    cli.hasOption(OPTION_REVERSE)
  }

  private def getChemaxonLicenseFile(cli: CommandLine): File = {
    new File(cli.getOptionValue(OPTION_LICENSE_FILE))
  }

  private def callProjector(cli: CommandLine)
                           (spark: SparkContext, validInchis: Stream[Stream[String]]): RDD[ProjectionResult] = {
    /* ------ Projection ------- */
    LOGGER.info("Building InChI RDD")
    // No need to launch a bunch of parallel tasks if we have a small InChI list.
    val chunkSize = 1000
    val groupSize: Int = (validInchis.length % chunkSize) + 1
    val inchiRDD: RDD[Seq[String]] = spark.makeRDD(validInchis, groupSize)

    LOGGER.info(s"Starting execution.  Projection ${validInchis.length} substrate groups.")
    // PROJECT!  Run ERO projection over all InChIs.
    scopedProjection(getChemaxonLicenseFile(cli).getName)(isReverse(cli), isExhaustive(cli))(inchiRDD)
  }

  private def scopedProjection(licenseFileName: String)
                              (reverse: Boolean, exhaustive: Boolean)
                              (inchiRDD: RDD[Seq[String]]): RDD[ProjectionResult] = {
    /**
      * We need to set the scope of the projection to as smaller unit so that
      * we don't need to ensure a bunch of other stuff serializes.
      */
    val mapper: List[String] => Stream[ProjectionResult] =
      SparkProjectionInstance.project(licenseFileName)(reverse, exhaustive)
    val seqMapper: Seq[String] => Stream[ProjectionResult] =
      i => mapper(i.toList)

    inchiRDD.flatMap(seqMapper)
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
    LOGGER.info(s"Projection completed with $resultCount results")
    results.toLocalIterator.toStream
  }

  private def cleanup(resultsRDD: RDD[ProjectionResult]): Unit = {
    resultsRDD.unpersist()
  }
}







/*

  private def inchiSourceFromDB(dbName: String, dbPort: Int, dbHost: String): Stream[Stream[String]] = {
    val db: MongoDB = new MongoDB(dbHost, dbPort, dbName)
    val reactionIter = new ValidReactionSubstratesIterator(db)

    JavaConverters.asScalaIteratorConverter(reactionIter).asScala.toStream.map(_.toList.toStream)
  }

  def getOptions(): Unit ={
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_READ_FROM_REACHABLES_DB).
        longOpt("reachables-db").
        desc("Specifies  to read input inchis from a reachables DB."),


      CliOption.builder(OPTION_DB_NAME).
        hasArg().
        longOpt("db-name").
        desc("The name of the mongo DB to use."),

      CliOption.builder(OPTION_WRITE_PROJECTIONS_TO_DB).
        longOpt("write-to-database").
        desc("Specifies to write the results into the supplied reachables database instead of writing to a file."),
    )
  }

  /**
    * Reads all the inchis from a reachables database, and returns them as an L2InchiCorpus.
    */
  private def readFromReachablesDatabase(database: String, port: Int, host: String): L2InchiCorpus = {
    val reachables = getReachablesCollection(database, port, host)
    val cursor: DBCursor = reachables.find()
    val inchis: ArrayBuffer[String] = ArrayBuffer[String]()

    while (cursor.hasNext) {
      val entry: DBObject = cursor.next
      val inchiString: String = entry.get("InChI").asInstanceOf[String]
      inchis.append(inchiString)
    }

    new L2InchiCorpus(inchis.toList.asJava)
  }

  /**
    * Writes the projection results to the reachables database.
    * Creates new database entries for each predicted product (if none exists), and adds the substrates
    * to that entry as precursors.
    */
  private def writeToReachablesDatabase(resultsRDD: RDD[ProjectionResult], database: String, port: Int, host: String): Unit = {
    val reachables = getReachablesCollection(database, port, host)

    val resultCount = resultsRDD.persist().count()
    LOGGER.info(s"Projection completed with $resultCount results")
    val resultsIterator = resultsRDD.toLocalIterator

    resultsIterator.foreach(projection => {
      val updater = new ReachablesProjectionUpdate(projection)
      updater.updateReachables(reachables)
    })
  }

  private def writeToReachablesDatabaseThroughLoader(resultsRDD: RDD[ProjectionResult], loader: Loader): Unit = {
    val resultCount = resultsRDD.persist().count()
    LOGGER.info(s"Projection completed with $resultCount results")
    val resultsIterator = resultsRDD.toLocalIterator

    resultsIterator.foreach(projection => {
      val updater = new ReachablesProjectionUpdate(projection)
      updater.updateByLoader(loader)
    })
  }

  /**
    * Helper method to get the reachables collection from the wiki_reachables DB or equivalent.
    */

  private def writeToJsonFile(resultsRDD: RDD[ProjectionResult], outputDir: File): Unit = {

    // Stream output to file so that we can keep our memory footprint low, while still writing files efficiently.
    val projectedReactionsFile = new File(outputDir, "projectedReactions")
    val buffer = new BufferedWriter(new FileWriter(projectedReactionsFile))

    // TODO Consider if we want to try using jackson/spray's streaming API?
    // Start array and write
    buffer.write("[")

    val resultsIterator = resultsRDD.toLocalIterator
    buffer.write(s"${resultsIterator.next().toJson.prettyPrint}")

    // For each element in the iterator, write as a new element
    // TODO Consider buffer flushing after each write?
    resultsIterator.foreach(result => {
      buffer.write(s",${result.toJson.prettyPrint}")
    })

    // Close up the array and close the file.
    buffer.write("]")
    buffer.close()

    resultsRDD.unpersist()
  }

  def projectInChIsAndReturnResults(chemaxonLicense: File, assembledJar: File, workingDirectory: File)
                                   (memory: String = "4GB", sparkMaster: String = DEFAULT_SPARK_MASTER)
                                   (inputInchis: List[L2InchiCorpus])
                                   (exhaustive: Boolean = false, reverse: Boolean = false): List[ProjectionResult] = {
    if (!workingDirectory.exists()) workingDirectory.mkdirs()

    val filePrefix = "tmpInchiCorpus"
    val fileSuffix = "txt"

    // Write to a file
    val substrateFiles: List[String] = inputInchis.zipWithIndex.map({
      case (file, index) =>
        val substrateFile = new File(workingDirectory, s"$filePrefix.$index.$fileSuffix")
        file.writeToFile(substrateFile)
        substrateFile.getAbsolutePath
    })

    val conditionalArgs: ListBuffer[String] = new ListBuffer()
    if (exhaustive) conditionalArgs.append(s"-$OPTION_EXHAUSTIVE")
    if (reverse) conditionalArgs.append(s"-$OPTION_REVERSE")


    val classArgs: List[String] = List(
      s"-$OPTION_LICENSE_FILE", chemaxonLicense.getAbsolutePath,
      s"-$OPTION_OUTPUT_DIRECTORY", workingDirectory.getAbsolutePath,
      s"-$OPTION_SUBSTRATES_LISTS", substrateFiles.mkString(",")
    ) ::: conditionalArgs.toList

    // We need to replace the $ because scala ends the canonical name with that.
    val sparkJob =
      SparkWrapper.runClassPath(
        assembledJar.getAbsolutePath, sparkMaster)(getClass.getCanonicalName.replaceAll("\\$$", ""), classArgs)(memory)

    // Block until finished
    JobManager.startJobAndAwaitUntilWorkflowComplete(sparkJob)

    // Reload file from disk
    val outputResults = new File(workingDirectory, "projectedReactions")

    // TODO Consider if it is worthwhile or desired to remove all created files,
    // effectively leveraging files just as temporary intermediates.

    scala.io.Source.fromFile(outputResults).getLines().mkString.parseJson.convertTo[List[ProjectionResult]]
  }
 */
