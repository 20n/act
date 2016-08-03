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

object compute {
  private val MS_PER_S = 1000.0d
  private val RUNTIME_WARNING_THRESHOLD_S = 60d * 15d; // 15 mins
  private val LOGGER = LogManager.getLogger(getClass)
  // Create one job per ERO to cut down on time spent compiling.  TODO: segment more efficiently and cache Eros.
  def run(licenseFileName: String, ero: Ero, inchis: List[String]): (Double, L2PredictionCorpus) = {
    val startTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val localLicenseFile = SparkFiles.get(licenseFileName)
    LOGGER.info(s"Using license file at $localLicenseFile (file exists: ${new File(localLicenseFile).exists()})")
    LicenseManager.setLicenseFile(localLicenseFile)
    val expander = new SingleSubstrateRoExpander(List(ero).asJava, inchis.asJava,
      new AllPredictionsGenerator(new ReactionProjector()))
    val endTime: DateTime = new DateTime().withZone(DateTimeZone.UTC)
    val deltaTS = (endTime.getMillis - startTime.getMillis).toDouble / MS_PER_S
    LOGGER.info(f"Running projection of ERO with id ${ero.getId} in $deltaTS%0.3f")
    if (deltaTS > RUNTIME_WARNING_THRESHOLD_S) {
      LOGGER.warn(s"ERO ${ero.getId} required excessive time to complete, please consider refining")
    }
    val results = expander.getPredictions(null)
    (deltaTS, results)
  }
}

object ROProjector {
  private val LOGGER = LogManager.getLogger(getClass)

  private val OBJECT_MAPPER = new ObjectMapper()

  val OPTION_LICENSE_FILE = "l"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_FILTER_FOR_SPECTROMETERY = "s"

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

    val eros = new ErosCorpus()
    eros.loadValidationCorpus()
    val fullErosList = eros.getRos.asScala
    LOGGER.info("Filtering eros to only those having names and single substrates")
    val erosList = fullErosList.filter(x => x.getName != null && !x.getName.isEmpty && x.getSubstrate_count == 1)
    LOGGER.info(s"Reduction in ERO list size: ${fullErosList.size} -> ${erosList.size}")

    val substratesListFile = cl.getOptionValue(OPTION_SUBSTRATES_LIST)
    val validInchis = Source.fromFile(substratesListFile).getLines().
      filter(x => x.startsWith("InChI=")).
      filter(x => try { MolImporter.importMol(x); true } catch { case e : Exception => false }).toList
    LOGGER.info(s"Loaded and validated ${validInchis.size} InChIs from source file at $substratesListFile")

    val inchis = if (cl.hasOption(OPTION_FILTER_FOR_SPECTROMETERY)) {
      LOGGER.info("Filtering candidate substrates to range accessibly by LCMS")
      val filtered = validInchis.filter(x => MolImporter.importMol(x).getMass <= 950.0d)
      LOGGER.info(s"Reduction in substrate list size: ${validInchis.size} -> ${filtered.size}")
      filtered
    } else {
      validInchis
    }

    val outputDir = new File(cl.getOptionValue(OPTION_OUTPUT_DIRECTORY))
    if (outputDir.exists() && !outputDir.isDirectory) {
      LOGGER.error(s"Found output directory at ${outputDir.getAbsolutePath} but is not a directory")
      exitWithHelp(getCommandLineOptions)
    } else {
      LOGGER.info(s"Creating output directory at ${outputDir.getAbsolutePath}")
      outputDir.mkdirs()
    }

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
    val resultsRDD: RDD[(Ero, Double, L2PredictionCorpus)] =
      eroRDD.map(ero => {
        val results = compute.run(licenseFileName, ero, inchis)
        (ero, results._1, results._2)
      })

    LOGGER.info("Collecting")
    val timingPairs: List[(Integer, Double)] = resultsRDD.map(triple => {
      LOGGER.info(s"Writing results for ERO ${triple._1.getId}")
      val outputFile = new File(outputDir, triple._1.getId.toString)
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, triple._3)
      (triple._1.getId, triple._2)
    }).collect().toList

    LOGGER.info("Projection execution time report:")
    timingPairs.sortWith((a, b) => b._2 >= a._2).foreach(pair => LOGGER.info(f"ERO ${pair._1}%4d: ${pair._2}%0.3f"))
    LOGGER.info("Done")
  }
}
