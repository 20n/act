package com.act.biointerpretation.l2expansion

import java.io.{BufferedWriter, File, FileWriter}

import chemaxon.license.LicenseManager
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.l2expansion.SparkROProjector.InchiResult
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import spray.json._

import scala.collection.JavaConverters._
import scala.io.Source

/**
  * A Spark job that will project the set of single-substrate validation EROs over a list of substrate InChIs.
  *
  * Run like:
  * $ sbt assembly
  * $ $SPARK_HOME/bin/spark-submit \
  * --driver-class-path $PWD/target/scala-2.10/reachables-assembly-0.1.jar \
  * --class com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector \
  * --master spark://spark-master:7077 \
  * --deploy-mode client --executor-memory 4G \
  * $PWD/target/scala-2.10/reachables-assembly-0.1.jar \
  * --substrates-list file_of_substrate_inchis \
  * -s \
  * -o output_file \
  * -l license file
  */
object SparkMoleculeProjector {
  val defaultMoleculeFormat = MoleculeFormat.strictNoStereoInchi

  private val LOGGER = LogManager.getLogger(getClass)
  var eros = new ErosCorpus()
  eros.loadValidationCorpus()

  var localLicenseFile: Option[String] = None

  def project(licenseFileName: String)
             (reverse: Boolean, exhaustive: Boolean)
             (substrates: List[String]): Stream[InchiResult] = {
    // Load license file once
    if (this.localLicenseFile.isEmpty) {
      this.localLicenseFile = Option(SparkFiles.get(licenseFileName))
      LOGGER.info(s"Using license file at $localLicenseFile " +
        s"(file exists: ${new File(SparkFiles.get(licenseFileName)).exists()})")
      LicenseManager.setLicenseFile(SparkFiles.get(licenseFileName))
    }

    LOGGER.info(s"Reverse is set to $reverse")
    val getResults: Ero => Stream[InchiResult] = getResultsForSubstrate(substrates, reverse, exhaustive)
    val results: Stream[InchiResult] = this.eros.getRos.asScala.toStream.flatMap(getResults)
    results
  }

  private def getResultsForSubstrate(inputs: List[String], reverse: Boolean, exhaustive: Boolean)
                                    (ro: Ero): Stream[InchiResult] = {
    // We check the substate or the products to ensure equal length based on if we are reversing the rxn or not.
    if (!reverse && ro.getSubstrate_count != inputs.length) {
      return Stream()
    }
    if (reverse && ro.getProduct_count != inputs.length) {
      return Stream()
    }

    // Setup reactor based on ro
    val reactor = ro.getReactor
    if (reverse) {
      reactor.setReverse(reverse)
    }

    // Get all permutations of the input so that substrate order doesn't matter.
    val resultingReactions: Stream[InchiResult] = inputs.permutations.flatMap(substrateOrdering => {

      // Setup reactor
      reactor.setReactants(
        substrateOrdering.map(MoleculeImporter.importMolecule(_, defaultMoleculeFormat)).toArray)

      // Return results
      val reactedValues: Stream[Array[Molecule]] = if (exhaustive) {
        Stream.continually(Option(reactor.react())).takeWhile(_.isDefined).flatMap(_.toStream)
      } else {
        val result = Option(reactor.react())
        if (result.isDefined) Stream(result.get) else Stream()
      }

      // Map the resulting reactions to a consistent format.
      val partiallyAppliedMapper: List[Molecule] => Option[InchiResult] = mapReactionsToResult(inputs, ro.getId.toString)
      reactedValues.flatMap(potentialProducts => partiallyAppliedMapper(potentialProducts.toList))
    }).toStream

    // Output stream
    resultingReactions
  }

  private def mapReactionsToResult(substrates: List[String], roNumber: String)(potentialProducts: List[Molecule]): Option[InchiResult] = {
    try {
      val products = potentialProducts.map(x => MoleculeExporter.exportMolecule(x, defaultMoleculeFormat))
      Option(InchiResult(substrates, roNumber, products))
    } catch {
      case e: MolExportException => None
    }
  }
}

object SparkROProjector {
  private val LOGGER = LogManager.getLogger(getClass)

  private val SPARK_LOG_LEVEL = "WARN"

  val OPTION_EXHAUSTIVE = "e"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_LICENSE_FILE = "l"
  val OPTION_SPARK_MASTER = "m"
  val OPTION_FILTER_REQUIRE_RO_NAMES = "n"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_NUMBER_SUBSTRATES = "p"
  val OPTION_REVERSE = "r"
  val OPTION_FILTER_FOR_SPECTROMETERY = "s"
  val OPTION_VALID_CHEMICAL_TYPE = "v"

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

      CliOption.builder(OPTION_VALID_CHEMICAL_TYPE).
        longOpt("valid-chemical-types").
        hasArg.
        desc("A molecule string format. Currently valid types are inchi, stdInchi, smiles, and smarts.  " +
          s"By default, uses stdInChI which " +
          s"is the format '${MoleculeFormat.getExportString(MoleculeFormat.stdInchi)}'.  " +
          s"Possible values are: \n${MoleculeFormat.listPossibleFormats().mkString("\n")}"),

      CliOption.builder(OPTION_SPARK_MASTER).
        longOpt("spark-master").
        desc("Where to look for the spark master connection. " +
          "Uses \"spark://spark-master:7077\" by default."),

      CliOption.builder(OPTION_NUMBER_SUBSTRATES).
        longOpt("substrate-number").
        hasArg.
        desc("The number of substrates the input reactions are expected to have.  " +
          "Be careful, a number greater than 1 permutes the initial " +
          "list so that we project on all pair-wise groupings."),

      CliOption.builder(OPTION_REVERSE).
        longOpt("reverse").
        desc("Flag to reverse all reactions."),

      CliOption.builder(OPTION_EXHAUSTIVE).
        longOpt("exhaustive").
        desc("Flag to indicate that substrates should be reacted until exhaustion, " +
          "meaning all possible reactions occur and are returned."),

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

    val substratesListFile = cl.getOptionValue(OPTION_SUBSTRATES_LIST)
    val inchiCorpus = new L2InchiCorpus()
    inchiCorpus.loadCorpus(new File(substratesListFile))


    if (cl.hasOption(OPTION_FILTER_FOR_SPECTROMETERY)) {
      LOGGER.info(s"Substrate list size before mass filtering: ${inchiCorpus.getInchiList.size}")
      inchiCorpus.filterByMass(950)
      LOGGER.info(s"Substrate list size after filtering: ${inchiCorpus.getInchiList.size}")
    }

    // We filter, but don't actually import here.  If we imported we'd run out of memory way faster.
    val substrateGroups: Set[String] = Source.fromFile(substratesListFile).getLines().toSet

    val pairs: Set[Seq[String]] = substrateGroups.toSeq.combinations(cl.getOptionValue(OPTION_NUMBER_SUBSTRATES, "1").toInt).toSet

    val validInchis: Set[Seq[String]] = pairs.filter(group => {
      try {
        group.foreach(inchi => {
          MoleculeImporter.importMolecule(inchi)
        })
        true
      } catch {
        case e: Exception => false
      }
    })

    LOGGER.info(s"Loaded and validated ${validInchis.size} InChIs from source file at $substratesListFile")


    // Don't set a master here, spark-submit will do that for us.
    val conf = new SparkConf().setAppName("Spark RO Projection").setMaster(cl.getOptionValue(OPTION_SPARK_MASTER, "spark://spark-master:7077"))
    conf.set("spark.scheduler.mode", "FAIR")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    // Silence Spark's verbose logging, which can make it difficult to find our own log messages.
    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing license file to spark workers")
    spark.addFile(licenseFile)
    val licenseFileName = new File(licenseFile).getName

    LOGGER.info("Building ERO RDD")
    val groupSize = 100
    // TODO Add file parsing so that multiple substrate reactions can be loaded in
    val inchiRDD: RDD[Seq[String]] = spark.makeRDD(validInchis.toList, groupSize)

    LOGGER.info("Starting execution")
    // PROJECT!  Run ERO projection over all InChIs.
    val resultsRDD: RDD[InchiResult] = inchiRDD.flatMap(inchi => {
      SparkMoleculeProjector.project(licenseFileName)(cl.hasOption(OPTION_REVERSE), cl.hasOption(OPTION_EXHAUSTIVE))(inchi.toList)
    })

    handleProjectionTermination(resultsRDD, outputDir)
  }

  def handleProjectionTermination(resultsRDD: RDD[InchiResult], outputDir: File): Unit = {
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
     * for more context on Spark's laziness.
     */
    val resultCount = resultsRDD.persist().count()
    LOGGER.info(s"Projection completed with $resultCount results")

    // Stream output to file so that we can keep our memory footprint low, while still writing files efficiently.
    val projectedReactionsFile = new File(outputDir, "projectedReactions")
    val buffer = new BufferedWriter(new FileWriter(projectedReactionsFile))

    // Start array and write
    buffer.write("[")

    // For each element in the iterator, write as a new element
    // TODO Consider buffer flushing after each write?
    resultsRDD.toLocalIterator.foreach(result => {
      buffer.write(s",${result.toJson.prettyPrint}")
    })

    // Close up the array and close the file.
    buffer.write("]")
    buffer.close()

    resultsRDD.unpersist()
  }

  case class InchiResult(substrate: List[String], ros: String, products: List[String])

}
