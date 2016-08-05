package com.act.biointerpretation.l2expansion

import java.io.File

import chemaxon.formats.MolImporter
import chemaxon.license.LicenseManager
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
  def run(licenseFileName: String, ero: Ero, inchis: List[String]): (Double, L2PredictionCorpus) = {
    val startTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val localLicenseFile = SparkFiles.get(licenseFileName)

    LOGGER.info(s"Using license file at $localLicenseFile (file exists: ${new File(localLicenseFile).exists()})")
    LicenseManager.setLicenseFile(localLicenseFile)

    val expander = new SingleSubstrateRoExpander(List(ero).asJava, inchis.asJava,
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

  val OPTION_LICENSE_FILE = "l"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_FILTER_FOR_SPECTROMETERY = "s"
  val OPTION_FILTER_REQUIRE_RO_NAMES = "n"

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
    val fullErosList = eros.getRos.asScala
    LOGGER.info("Filtering eros to only those having names and single substrates")
    val erosList = (if (cl.hasOption(OPTION_FILTER_REQUIRE_RO_NAMES)) {
      fullErosList.filter(x => x.getName != null && !x.getName.isEmpty)
    } else {
      fullErosList
    }).filter(_.getSubstrate_count == 1)
    LOGGER.info(s"Reduction in ERO list size: ${fullErosList.size} -> ${erosList.size}")

    val substratesListFile = cl.getOptionValue(OPTION_SUBSTRATES_LIST)
    val validInchis = Source.fromFile(substratesListFile).getLines().
      filter(_.startsWith("InChI=")).
      filter(x => try { MolImporter.importMol(x); true } catch { case e : Exception => false }).toList
    LOGGER.info(s"Loaded and validated ${validInchis.size} InChIs from source file at $substratesListFile")

    val inchis = validInchis.filter(
      x => if (cl.hasOption(OPTION_FILTER_FOR_SPECTROMETERY)) MolImporter.importMol(x).getMass <= 950.0d else true)
    LOGGER.info(s"Reduction in substrate list size after filtering: ${validInchis.size} -> ${inchis.size}")

    // Don't set a master here, spark-submit will do that for us.
    val conf = new SparkConf().setAppName("Spark RO Projection")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    LOGGER.info("Distributing license file to spark workers")
    spark.addFile(licenseFile)
    val licenseFileName = new File(licenseFile).getName

    LOGGER.info("Building ERO RDD")
    val eroRDD: RDD[Ero] = spark.makeRDD(erosList, erosList.size)

    LOGGER.info("Starting execution")
    // PROJECT!  Run ERO projection over all InChIs.
    val resultsRDD: RDD[(Ero, Double, L2PredictionCorpus)] =
      eroRDD.map(ero => {
        val results = compute.run(licenseFileName, ero, inchis)
        (ero, results._1, results._2)
      })

    LOGGER.info("Collecting")
    // Map over results and runtime, writing results to appropriate output files and collecting ids/runtime for reports.
    val mapper: (Ero, Double, L2PredictionCorpus) => (Integer, Double) = (ero, runtime, projectionResults) => {
      LOGGER.info(s"Writing results for ERO ${ero.getId}")
      val outputFile = new File(outputDir, ero.getId.toString)
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, projectionResults)
      (ero.getId, runtime)
    }

    // DO THE THING!  Run the mapper on all the executors over the projection results, and collect timing info.
    val timingPairs: List[(Integer, Double)] = resultsRDD.collect().map(t => mapper(t._1, t._2, t._3)).toList

    LOGGER.info("Projection execution time report:")
    timingPairs.sortWith((a, b) => b._2 < a._2).foreach(pair => LOGGER.info(f"ERO ${pair._1}%4d: ${pair._2}%.3fs"))
    LOGGER.info("Done")
  }
}
