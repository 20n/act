package com.act.biointerpretation.l2expansion

import java.io.{BufferedWriter, File, FileWriter}

import breeze.linalg
import breeze.linalg.reverse
import chemaxon.license.LicenseManager
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.l2expansion.SparkROProjector.InchiResult
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.jobs.{Job, ShellJob}
import com.act.workflow.tool_manager.tool_wrappers.SparkWrapper
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

// TODO Allow to be called from outside of CLI so we can programmatically spark project (Possibly using Spark Workflow)?
object SparkMoleculeProjector {
  val defaultMoleculeFormat = MoleculeFormat.strictNoStereoInchi

  private val LOGGER = LogManager.getLogger(getClass)
  var eros = new ErosCorpus()
  eros.loadValidationCorpus()

  var localLicenseFile: Option[String] = None

  def project(licenseFileName: String)
             (reverse: Boolean, exhaustive: Boolean)
             (substrates: List[String]): Stream[InchiResult] = {

    // Load Chemaxon license file once
    if (this.localLicenseFile.isEmpty) {
      this.localLicenseFile = Option(SparkFiles.get(licenseFileName))
      LOGGER.info(s"Using license file at $localLicenseFile " +
        s"(file exists: ${new File(SparkFiles.get(licenseFileName)).exists()})")
      LicenseManager.setLicenseFile(SparkFiles.get(licenseFileName))
    }

    val getResults: Ero => Stream[InchiResult] = getResultsForSubstrate(substrates, reverse, exhaustive)
    val results: Stream[InchiResult] = this.eros.getRos.asScala.toStream.flatMap(getResults)
    results
  }

  private def getResultsForSubstrate(inputs: List[String], reverse: Boolean, exhaustive: Boolean)
                                    (ro: Ero): Stream[InchiResult] = {
    // We check the substrate or the products to ensure equal length based on if we are reversing the rxn or not.
    if ((!reverse && ro.getSubstrate_count != inputs.length) || (reverse && ro.getProduct_count != inputs.length)) {
      return Stream()
    }

    // Setup reactor based on ro
    val reactor = ro.getReactor
    if (reverse) reactor.setReverse(reverse)

    // Get all permutations of the input so that substrate order doesn't matter.
    val resultingReactions: Stream[InchiResult] = inputs.permutations.flatMap(substrateOrdering => {

      // Setup reactor
      reactor.setReactants(substrateOrdering.map(MoleculeImporter.importMolecule(_, defaultMoleculeFormat)).toArray)

      // Generate reactions
      val reactedValues: Stream[Array[Molecule]] = if (exhaustive) {
        Stream.continually(Option(reactor.react())).takeWhile(_.isDefined).flatMap(_.toStream)
      } else {
        val result = Option(reactor.react())
        if (result.isDefined) Stream(result.get) else Stream()
      }

      // Map the resulting reactions to a consistent format.
      val partiallyAppliedMapper: List[Molecule] => Option[InchiResult] =
        mapReactionsToResult(inputs, ro.getId.toString)

      reactedValues.flatMap(potentialProducts => partiallyAppliedMapper(potentialProducts.toList))
    }).toStream

    // Output stream
    resultingReactions
  }

  private def mapReactionsToResult(substrates: List[String], roNumber: String)
                                  (potentialProducts: List[Molecule]): Option[InchiResult] = {
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
  private val DEFAULT_SPARK_MASTER = "spark://spark-master:7077"

  val OPTION_EXHAUSTIVE = "e"
  val OPTION_SUBSTRATES_LISTS = "i"
  val OPTION_LICENSE_FILE = "l"
  val OPTION_SPARK_MASTER = "m"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_REVERSE = "r"
  val OPTION_VALID_CHEMICAL_TYPE = "v"

  def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file")
        .desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

      CliOption.builder(OPTION_SUBSTRATES_LISTS).
        required(true).
        hasArgs.
        longOpt("substrates-list").
        desc("A list of substrate InChIs onto which to project ROs"),

      CliOption.builder(OPTION_OUTPUT_DIRECTORY).
        required(true).
        hasArg.
        longOpt("output-directory").
        desc("A directory in which to write per-RO result files"),

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
          s"Uses \" $DEFAULT_SPARK_MASTER \" by default."),

      CliOption.builder(OPTION_REVERSE).
        longOpt("reverse").
        desc("Flag to reverse all reactions."),

      CliOption.builder(OPTION_EXHAUSTIVE).
        longOpt("exhaustive").
        desc("Flag to indicate that substrates should be reacted until exhaustion, " +
          "meaning all possible reactions occur and are returned.  " +
          "Can be quite expensive for substrates with a large quantity of reaction sites."),

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

    val substrateCorpuses: List[L2InchiCorpus] = cl.getOptionValues(OPTION_SUBSTRATES_LISTS).toList.map(x => {
      val inchiCorpus = new L2InchiCorpus()
      inchiCorpus.loadCorpus(new File(x))
      inchiCorpus
    })

    // List of all unique InChIs in each corpus
    val inchiCorpuses: List[List[String]] = substrateCorpuses.map(_.getInchiList.asScala.distinct.toList)

    // List of combinations of InChIs
    val inchiCombinations: List[List[String]] = combinationList(inchiCorpuses)

    val validInchis: List[List[String]] = inchiCombinations.filter(group => {
      try {
        group.foreach(inchi => {
          MoleculeImporter.importMolecule(inchi)
        })
        true
      } catch {
        case e: Exception => false
      }
    })

    // Setup spark connection
    val conf = new SparkConf().
      setAppName("Spark RO Projection").
      setMaster(cl.getOptionValue(OPTION_SPARK_MASTER, DEFAULT_SPARK_MASTER))
    conf.set("spark.scheduler.mode", "FAIR")
    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    // Silence Spark's verbose logging, which can make it difficult to find our own log messages.
    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing Chemaxon license file to spark workers")
    spark.addFile(licenseFile)
    val licenseFileName = new File(licenseFile).getName

    LOGGER.info("Building ERO RDD")
    val groupSize = 100

    val inchiRDD: RDD[Seq[String]] = spark.makeRDD(validInchis, groupSize)

    LOGGER.info("Starting execution")
    // PROJECT!  Run ERO projection over all InChIs.
    val resultsRDD: RDD[InchiResult] = inchiRDD.flatMap(inchi => {
      SparkMoleculeProjector.project(licenseFileName)(
        cl.hasOption(OPTION_REVERSE), cl.hasOption(OPTION_EXHAUSTIVE))(inchi.toList)
    })

    handleProjectionTermination(resultsRDD, outputDir)
  }

  def handleProjectionTermination(resultsRDD: RDD[InchiResult], outputDir: File): Unit = {
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
    val resultCount = resultsRDD.persist().count()
    LOGGER.info(s"Projection completed with $resultCount results")

    // Stream output to file so that we can keep our memory footprint low, while still writing files efficiently.
    val projectedReactionsFile = new File(outputDir, "projectedReactions")
    val buffer = new BufferedWriter(new FileWriter(projectedReactionsFile))

    // TODO Consider if we want to try using jackson/spray's streaming API?
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

  private def combinationList(suppliedInchiLists: List[List[String]]): List[List[String]] = {
    val filteredLists = suppliedInchiLists.filter(_.nonEmpty)

    val validCombinations: ListBuffer[List[String]] = mutable.ListBuffer()

    // Instantiate pointers at the first element.
    val pointers: Array[Int] = Array.fill(filteredLists.size)(0)

    while (pointers.head < filteredLists.head.length) {
      // Get the next sequence and append to our combinations list
      val indexedPointers = pointers.zipWithIndex
      validCombinations.append(indexedPointers.map({
        case (value, index) => filteredLists(index)(value)
      }).toList)

      // Handle pointers
      // Find the last value that is below its end.  If it is at the end, reset to 0 and increment the next one.
      val iteratePointer =
        indexedPointers.reverse.find({ case (value, index) => value <= filteredLists(index).length }).get

      // Count up the pointers
      if (iteratePointer._1 < filteredLists(iteratePointer._2).length - 1) {
        pointers(iteratePointer._2) += 1
      } else {
        var index = iteratePointer._2
        pointers(index) += 1
        while (pointers(index) == filteredLists(index).length && index > 0) {
          pointers(index) = 0
          pointers(index - 1) += 1
          index -= 1
        }
      }
    }

    validCombinations.toList.distinct
  }

  def projectInChIsAndReturnResults(chemaxonLicense: File, workingDirectory: File)
                                   (memory: String = "4GB", sparkMaster: String = DEFAULT_SPARK_MASTER, useCached: Boolean = false)
                                   (inputInchis: List[L2InchiCorpus])
                                   (exhaustive: Boolean = false, reverse: Boolean = false): List[InchiResult] = {

    val assembledJar = "target/scala-2.10/reachables-assembly-0.1.jar"
    val sparkJob: Job = SparkWrapper.assembleJarAtRuntime(assembledJar, useCached)

    if (!workingDirectory.exists()) workingDirectory.mkdirs()

    val filePrefix = "inchiCorpus"
    val fileSuffix = "txt"

    // Write to a file
    val substrateFiles: List[String] = inputInchis.zipWithIndex.map(
      {case(file, index) =>
        val substrateFile = new File(workingDirectory, s"$filePrefix.$index.$fileSuffix")
        file.writeToFile(substrateFile)
        substrateFile.getAbsolutePath
      }
    )

    val classArgs: List[String] = List(
      s"-$OPTION_EXHAUSTIVE", exhaustive.toString,
      s"-$OPTION_REVERSE", reverse.toString,
      s"-$OPTION_LICENSE_FILE", chemaxonLicense.getAbsolutePath,
      s"-$OPTION_OUTPUT_DIRECTORY", workingDirectory.getAbsolutePath,
      s"-$OPTION_SUBSTRATES_LISTS", substrateFiles.mkString(",")
    )

    sparkJob.thenRun(
      SparkWrapper.runClassPath(assembledJar, sparkMaster)(getClass.getCanonicalName, List())(memory))

    // Block until finished
    JobManager.startJobAndAwaitUntilWorkflowComplete(sparkJob)

    val outputResults = new File(workingDirectory, "projectedReactions")
    scala.io.Source.fromFile(outputResults).getLines().mkString.parseJson.convertTo[List[InchiResult]]
  }



  case class InchiResult(substrate: List[String], ros: String, products: List[String])

}
