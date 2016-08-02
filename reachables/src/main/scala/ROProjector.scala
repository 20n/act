package com.act.biointerpretation.l2expansion

import java.io.File

import chemaxon.license.LicenseManager
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.JavaConverters._
import scala.io.Source

object compute {
  // Create one job per ERO to cut down on time spent compiling.  TODO: segment more efficiently and cache Eros.
  def run(licenseFile: String, ero: Ero, inchis: List[String]): L2PredictionCorpus = {
    LicenseManager.setLicenseFile(licenseFile)
    val expander = new SingleSubstrateRoExpander(List(ero).asJava, inchis.asJava,
      new AllPredictionsGenerator(new ReactionProjector()))
    expander.getPredictions(null)
  }
}

object ROProjector {
  private val LOGGER = LogManager.getLogger(getClass)

  private val OBJECT_MAPPER = new ObjectMapper()

  val OPTION_LICENSE_FILE = "l"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_SPARK_MASTER= "m"
  val OPTION_SPARK_HOME_DIR = "w"

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

      CliOption.builder(OPTION_SPARK_MASTER).
        required(true).
        hasArg.
        longOpt("spark-master").
        desc("URL to the spark master host"),

      CliOption.builder(OPTION_SPARK_HOME_DIR).
        required(true).
        hasArg.
        longOpt("spark-home").
        desc("Path to the spark home directory, probably /var/20n/spark"),

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
    //val cl = parseCommandLineOptions(args)

    //val licenseFile = cl.getOptionValue(OPTION_LICENSE_FILE)
    val licenseFile = "/var/20n/spark/chemaxon_license_Start-up.cxl"
    //LicenseManager.setLicenseFile(licenseFile)

    val eros = new ErosCorpus()
    eros.loadValidationCorpus()
    val erosList = eros.getRos.asScala

    //val substratesListFile = cl.getOptionValue(OPTION_SUBSTRATES_LIST)
    val substratesListFile = "/mnt/shared-data/Gil/untargetted_metabolomics/reachables_list"
    val inchis = Source.fromFile(substratesListFile).getLines().
      filter(x => x.startsWith("InChI=")).toList

    //val outputDir = new File(cl.getOptionValue(OPTION_OUTPUT_DIRECTORY))
    val outputDir = new File("/mnt/shared-data/Mark/spark_ro_projections")
    if (outputDir.exists() && !outputDir.isDirectory) {
      LOGGER.error(s"Found output directory at ${outputDir.getAbsolutePath} but is not a directory")
      exitWithHelp(getCommandLineOptions)
    } else {
      LOGGER.info(s"Creating output directory at ${outputDir.getAbsolutePath}")
      outputDir.mkdirs()
    }

    val conf = new SparkConf().setAppName("Spark RO Projection")
      //setMaster(cl.getOptionValue(OPTION_SPARK_MASTER)).setSparkHome(cl.getOptionValue(OPTION_SPARK_HOME_DIR))

    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    val eroRDD: RDD[Ero] = spark.makeRDD(erosList, erosList.size)

    val resultsRDD: RDD[(Ero, L2PredictionCorpus)] =
      eroRDD.map(ero => (ero, compute.run(licenseFile, ero, inchis)))

    resultsRDD.foreach(pair => {
      LOGGER.info("Writing results for ERO %d", pair._1.getId)
      val outputFile = new File(outputDir, pair._1.getId.toString)
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, pair._2)
    })
  }
}
