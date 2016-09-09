package com.act.biointerpretation.l2expansion

import java.io.File

import chemaxon.license.LicenseManager
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.{MoleculeExporter, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.io.Source

/**
  * A Spark job that will project the set of single-substrate validation EROs over a list of substrate InChIs.
  *
  * Run like:
  * $ sbt assembly
  * $ $SPARK_HOME/bin/spark-submit \
  *   --driver-class-path $PWD/target/scala-2.10/reachables-assembly-0.1.jar \
  *   --class com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector \
  *   --master spark://spark-master:7077 \
  *   --deploy-mode client --executor-memory 4G \
  *   $PWD/target/scala-2.10/reachables-assembly-0.1.jar \
  *   --substrates-list file_of_substrate_inchis \
  *   -s \
  *   -o output_file \
  *   -l license file
  */
object compute {
  private val MS_PER_S = 1000.0d
  private val RUNTIME_WARNING_THRESHOLD_S = 60d * 15d // 15 mins
  private val LOGGER = LogManager.getLogger(getClass)

  /* The current parallelism scheme partitions projections by ERO, using one worker thread to project one ERO over all
   * input InChIs.  Given the respective size of our sets of EROs and InChIs (i.e. ERO << InChIs), it might make sense
   * to partition on InChIs and run all ERO projections over small InChI groups.  However, the absence of obvious
   * pre-execution set up hooks for Spark executors means that we might end up compiling the EROs (at worst) once per
   * InChI, meaning we'd probably spend more time compiling EROs than we would running the projections.  Yikes!
   * We'd also need a separate shuffle and sort phase (which might be trivial?) to group together results by ERO id to
   * give us the nice one-ERO-per-output-file result partitioning we enjoy today.
   *
   * The current scheme gives us a big performance boost over serial RO projections.  That said, we can probably do
   * much better with added investigation.
   *
   * TODO: try out other partitioning schemes and/or pre-compile and cache ERO Reactors for improved performance.
   */
  def run(licenseFileName: String, ero: Ero, molecules: List[Molecule]): (Double, L2PredictionCorpus) = {
    val startTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val localLicenseFile = SparkFiles.get(licenseFileName)

    LOGGER.info(s"Using license file at $localLicenseFile (file exists: ${new File(localLicenseFile).exists()})")
    LicenseManager.setLicenseFile(localLicenseFile)

    val expander = new SingleSubstrateRoExpander(new ErosCorpus(List(ero).asJava), molecules.asJava,
      new AllPredictionsGenerator(new ReactionProjector()))

    val results = expander.getPredictions()

    val endTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val deltaTS = (endTime.getMillis - startTime.getMillis).toDouble / MS_PER_S
    LOGGER.info(f"Completed projection of ERO ${ero.getId} in $deltaTS%.3fs")
    if (deltaTS > RUNTIME_WARNING_THRESHOLD_S) {
      LOGGER.warn(s"ERO ${ero.getId} required excessive time to complete, please consider refining")
    }
    (deltaTS, results)
  }
}

object SparkSingleSubstrateROProjector {
  private val LOGGER = LogManager.getLogger(getClass)

  private val OBJECT_MAPPER = new ObjectMapper()

  private val SPARK_LOG_LEVEL = "WARN"

  val OPTION_LICENSE_FILE = "l"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_FILTER_FOR_SPECTROMETERY = "s"
  val OPTION_FILTER_REQUIRE_RO_NAMES = "n"
  val OPTION_VALID_CHEMICAL_TYPES = "c"

  def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file")
        .desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

      CliOption.builder(OPTION_SUBSTRATES_LIST).
        required(true).
        hasArg.
        longOpt("substrates-list").
        desc("A list of substrate InChIs onto which to project ROs"),

      CliOption.builder(OPTION_OUTPUT_DIRECTORY).
        required(true).
        hasArg.
        longOpt("output-directory").
        desc("A directory in which to write per-RO result files"),

      CliOption.builder(OPTION_FILTER_FOR_SPECTROMETERY).
        longOpt("filter-for-spectrometery").
        desc("Filter potential substrates to those that we think could be detected via LCMS (i.e. <= 950 daltons"),

      CliOption.builder(OPTION_FILTER_REQUIRE_RO_NAMES).
        longOpt("only-named-eros").
        desc("Only apply EROs from the validation corpus that have assigned names"),

      CliOption.builder(OPTION_VALID_CHEMICAL_TYPES).
        longOpt("valid-chemical-types").
        hasArgs.
        valueSeparator(',').
        desc("A comma divided list of valid chemical types to import the substrate list as.  " +
          "If your file is mixed, multiple can be provided." +
          "Currently valid types are inchi, smiles, and smarts.  " +
          "By default, only imports inchi."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE = "A Spark job that will project the set of validation ROs over a list of substrates."
  HELP_FORMATTER.setWidth(100)

  // The following were stolen (in haste) from Workflow.scala.
  def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

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

  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  def main(args: Array[String]): Unit = {
    val cl = parseCommandLineOptions(args)

    val licenseFile = cl.getOptionValue(OPTION_LICENSE_FILE)
    LOGGER.info(s"Validating license file at $licenseFile")
    LicenseManager.setLicenseFile(licenseFile)

    val outputDir = new File(cl.getOptionValue(OPTION_OUTPUT_DIRECTORY))
    if (outputDir.exists() && !outputDir.isDirectory) {
      LOGGER.error(s"Found output directory at ${outputDir.getAbsolutePath} but is not a directory")
      exitWithHelp(getCommandLineOptions)
    } else {
      LOGGER.info(s"Creating output directory at ${outputDir.getAbsolutePath}")
      outputDir.mkdirs()
    }

    val eros = new ErosCorpus()
    eros.loadValidationCorpus()
    val fullErosList = eros.getRos

    LOGGER.info("Filtering down to only one substrate ROs.")
    if (cl.hasOption(OPTION_FILTER_REQUIRE_RO_NAMES)) {
      LOGGER.info("Filtering down to only ROs with names.")
      eros.retainNamedRos()
    }

    val erosList = eros.getRos.asScala
    LOGGER.info(s"Reduction in ERO list size: ${fullErosList.size} -> ${erosList.size}")

    // Allow for multiple chemical types
    val chemicalFormats: List[MoleculeImporter.ChemicalFormat.MoleculeType] = if (cl.hasOption(OPTION_VALID_CHEMICAL_TYPES)) {
      val options = cl.getOptionValues(OPTION_VALID_CHEMICAL_TYPES).toList
      options.map(x => MoleculeImporter.ChemicalFormat.withName(x).toString)
    } else {
      List(MoleculeImporter.ChemicalFormat.Inchi)
    }

    val molExporterFormat = chemicalFormats.map(x => MoleculeExporter.ChemicalSetting.withName(x).toString)
    MoleculeExporter.setGlobalFormat(molExporterFormat)
    LOGGER.info(s"Molecule exporter status set to $molExporterFormat")

    val substratesListFile = cl.getOptionValue(OPTION_SUBSTRATES_LIST)
    val inchiCorpus = new L2InchiCorpus()
    inchiCorpus.loadCorpus(new File(substratesListFile))


    if (cl.hasOption(OPTION_FILTER_FOR_SPECTROMETERY)) {
      LOGGER.info(s"Substrate list size before mass filtering: ${inchiCorpus.getInchiList.size}")
      inchiCorpus.filterByMass(950)
      LOGGER.info(s"Substrate list size after filtering: ${inchiCorpus.getInchiList.size}")
    }

    val validMolecules: List[String] = Source.fromFile(substratesListFile).getLines().
      filter(x => try { MoleculeImporter.importMolecule(x, chemicalFormats); true } catch { case e : Exception => false }).toList
    LOGGER.info(s"Loaded and validated ${validMolecules.size} InChIs from source file at $substratesListFile")

    val validatedMolecules = validMolecules.map(MoleculeImporter.importMolecule(_, chemicalFormats))

    // Don't set a master here, spark-submit will do that for us.
    val conf = new SparkConf().setAppName("Spark RO Projection")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    // Silence Spark's verbose logging, which can make it difficult to find our own log messages.
    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing license file to spark workers")
    spark.addFile(licenseFile)
    val licenseFileName = new File(licenseFile).getName

    LOGGER.info("Building ERO RDD")
    val eroRDD: RDD[Ero] = spark.makeRDD(erosList, erosList.size)

    LOGGER.info("Starting execution")
    // PROJECT!  Run ERO projection over all InChIs.
    val resultsRDD: RDD[(Ero, Double, L2PredictionCorpus)] =
      eroRDD.map(ero => {
        val results = compute.run(licenseFileName, ero, validatedMolecules)
        (ero, results._1, results._2)
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
    LOGGER.info(s"Projection completed with $resultCount results")

    // Map over results and runtime, writing results to appropriate output files and collecting ids/runtime for reports.
    val mapper: (Ero, Double, L2PredictionCorpus) => (Integer, Double) = (ero, runtime, projectionResults) => {
      val outputFile = new File(outputDir, ero.getId.toString)
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, projectionResults)
      LOGGER.info(s"Wrote results for ERO ${ero.getId} (${outputFile.getTotalSpace}B)")
      (ero.getId, runtime)
    }

    // DO THE THING!  Run the mapper on all the executors over the projection results, and collect timing info.

    // Output results one at a time and collect timing info.
    val timingPairs: List[(Integer, Double)] = resultsRDD.toLocalIterator.map(t => mapper(t._1, t._2, t._3)).toList

    // Release the RDD now that we're done reading it.
    resultsRDD.unpersist()

    LOGGER.info("Projection execution time report:")
    timingPairs.sortWith((a, b) => b._2 < a._2).foreach(pair => LOGGER.info(f"ERO ${pair._1}%4d: ${pair._2}%.3fs"))
    LOGGER.info("Done")
  }
}
