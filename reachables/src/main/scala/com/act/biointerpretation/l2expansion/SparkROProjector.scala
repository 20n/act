package com.act.biointerpretation.l2expansion

import java.io.{BufferedWriter, File, FileWriter}

import chemaxon.license.LicenseManager
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.tool_wrappers.SparkWrapper
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, SparkFiles}
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

// Basic storage class for serializing and deserializing projection results
case class ProjectionResult(substrate: List[String], ros: String, products: List[String])

object ProjectionResultProtocol extends DefaultJsonProtocol {
  implicit val projectionFormat = jsonFormat3(ProjectionResult)
}

// Because we made the protocol in this file, we need to import it here to expose the methods it has to our file.
// This allows us to do direct conversions on ProjectionResult objects to and from JSON.
import com.act.biointerpretation.l2expansion.ProjectionResultProtocol._

object SparkInstance {
  val defaultMoleculeFormat = MoleculeFormat.strictNoStereoInchi

  private val LOGGER = LogManager.getLogger(getClass)
  var eros = new ErosCorpus()
  eros.loadValidationCorpus()

  var localLicenseFile: Option[String] = None

  def project(licenseFileName: String)
             (reverse: Boolean, exhaustive: Boolean)
             (substrates: List[String]): Stream[ProjectionResult] = {

    // Load Chemaxon license file once
    if (this.localLicenseFile.isEmpty) {
      this.localLicenseFile = Option(SparkFiles.get(licenseFileName))
      LOGGER.info(s"Using license file at $localLicenseFile " +
        s"(file exists: ${new File(SparkFiles.get(licenseFileName)).exists()})")
      LicenseManager.setLicenseFile(SparkFiles.get(licenseFileName))
    }

    val getResults: Ero => Stream[ProjectionResult] = getResultsForSubstrate(substrates, reverse, exhaustive)
    val results: Stream[ProjectionResult] = this.eros.getRos.asScala.toStream.flatMap(getResults)
    results
  }

  private def getResultsForSubstrate(inputs: List[String], reverse: Boolean, exhaustive: Boolean)
                                    (ro: Ero): Stream[ProjectionResult] = {
    // We check the substrate or the products to ensure equal length based on if we are reversing the rxn or not.
    if ((!reverse && ro.getSubstrate_count != inputs.length) || (reverse && ro.getProduct_count != inputs.length)) {
      return Stream()
    }

    // Setup reactor based on ro
    val reactor = ro.getReactor
    if (reverse) reactor.setReverse(reverse)

    // Get all permutations of the input so that substrate order doesn't matter.
    //
    // TODO Having the importedMolecules above the loop may interfere with permutation's ability to correctly
    // filter out duplicate reactions (For example, List(a,b,b) should not making two combinations (a,b,b).
    // We should assess this as for higher number of molecule reactors this may be the dominant case over the
    // cost that could be imposed by hitting the MoleculeImporter's cache.
    val importedMolecules: List[Molecule] = inputs.map(x => MoleculeImporter.importMolecule(x, defaultMoleculeFormat))
    val resultingReactions: Stream[ProjectionResult] = importedMolecules.permutations.flatMap(substrateOrdering => {

      // Setup reactor
      reactor.setReactants(substrateOrdering.toArray)

      // Generate reactions
      val reactedValues: Stream[Array[Molecule]] = if (exhaustive) {
        Stream.continually(Option(reactor.react())).takeWhile(_.isDefined).flatMap(_.toStream)
      } else {
        val result = Option(reactor.react())
        if (result.isDefined) Stream(result.get) else Stream()
      }

      // Map the resulting reactions to a consistent format.
      val partiallyAppliedMapper: List[Molecule] => Option[ProjectionResult] =
        mapReactionsToResult(inputs, ro.getId.toString)

      reactedValues.flatMap(potentialProducts => partiallyAppliedMapper(potentialProducts.toList))
    }).toStream

    // Output stream
    resultingReactions
  }

  private def mapReactionsToResult(substrates: List[String], roNumber: String)
                                  (potentialProducts: List[Molecule]): Option[ProjectionResult] = {
    try {
      val products = potentialProducts.map(x => MoleculeExporter.exportMolecule(x, defaultMoleculeFormat))
      Option(ProjectionResult(substrates, roNumber, products))
    } catch {
      case e: MolExportException =>
        LOGGER.warn(s"Unable to export a projected project.  Projected projects were $potentialProducts")
        None
    }
  }
}

object SparkROProjector {
  val OPTION_EXHAUSTIVE = "e"
  val OPTION_SUBSTRATES_LISTS = "i"
  val OPTION_LICENSE_FILE = "l"
  val OPTION_SPARK_MASTER = "m"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_REVERSE = "r"
  val OPTION_VALID_CHEMICAL_TYPE = "v"

  private val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)

  private val HELP_MESSAGE = "A Spark job that will project the set of validation ROs over a list of substrates."
  private val LOGGER = LogManager.getLogger(getClass)
  private val SPARK_LOG_LEVEL = "WARN"
  private val DEFAULT_SPARK_MASTER = "spark://spark-master:7077"

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

    LOGGER.info("Attempting to filter out combinations with invalid InChIs.  " +
      s"Starting with ${inchiCombinations.length} inchis.")
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
    LOGGER.info(s"Filtering removed ${inchiCombinations.length - validInchis.length}" +
      s" combinations, ${validInchis.length} remain.")

    // Setup spark connection
    val conf = new SparkConf().
      setAppName(s"${if (cl.hasOption(OPTION_EXHAUSTIVE)) "Exhaustive"} ${if (cl.hasOption(OPTION_REVERSE)) "Reverse"} " +
        s"${validInchis.head.length} Substrate${if (validInchis.head.length != 1) "s"} RO Projection").
      setMaster(cl.getOptionValue(OPTION_SPARK_MASTER, DEFAULT_SPARK_MASTER))

    // Allow multiple jobs to allocate
    conf.set("spark.scheduler.mode", "FAIR")

    conf.getAll.foreach(x => LOGGER.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    // Silence Spark's verbose logging, which can make it difficult to find our own log messages.
    spark.setLogLevel(SPARK_LOG_LEVEL)

    LOGGER.info("Distributing Chemaxon license file to spark workers")
    spark.addFile(licenseFile)
    val licenseFileName = new File(licenseFile).getName

    LOGGER.info("Building ERO RDD")
    val groupSize = 1000
    val inchiRDD: RDD[Seq[String]] = spark.makeRDD(validInchis, groupSize)

    LOGGER.info("Starting execution")
    // PROJECT!  Run ERO projection over all InChIs.
    val resultsRDD: RDD[ProjectionResult] = inchiRDD.flatMap(inchi => {
      SparkInstance.project(licenseFileName)(cl.hasOption(OPTION_REVERSE), cl.hasOption(OPTION_EXHAUSTIVE))(inchi.toList)
    })

    handleProjectionTermination(resultsRDD, outputDir)
  }

  private def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file").
        desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

      CliOption.builder(OPTION_SUBSTRATES_LISTS).
        required(true).
        hasArgs.
        valueSeparator(',').
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

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  // The following were stolen (in haste) from Workflow.scala.
  private def parseCommandLineOptions(args: Array[String]): CommandLine = {
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

  private def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  private def handleProjectionTermination(resultsRDD: RDD[ProjectionResult], outputDir: File): Unit = {
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

  private def combinationList(suppliedInchiLists: List[List[String]]): List[List[String]] = {
    // The reason for this method as opposed to other examples is that normally this process is done recursively;
    // however, that means that hitting a stack overflow is much easier.
    // Example of normal: http://stackoverflow.com/q/23425930/4978569
    // To avoid this, I've written a simple iterative algorithm that avoids the recursive stack problems.
    // An optimal solution to this problem would find a way to effectively leverage tail recursion to keep the
    // stack small, while simplifying the logic (As the stack frame that does exist effectively will manage
    // all the pointer stuff).
    //
    // The goal of this method is to take in a list containing a list of InChIs and to group them into all combinations.
    // For example, if three lists are supplied we will output all
    // possible groups of three where, for example, the first element is from the first list.
    val filteredLists = suppliedInchiLists.filter(_.nonEmpty)

    val validCombinations: ListBuffer[List[String]] = mutable.ListBuffer()

    // Instantiate pointers at the first element (0th).
    // The pointer array is of size `filteredLists` because we want one pointer for each list,
    // as we will be taking one element from each list on each pass.
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
        // If a pointer has exceeded the limit, we need to carry until it is resolved.
        var index = iteratePointer._2
        pointers(index) += 1

        // Terminate when we either are trying to set the first index (Meaning the one that the above while
        // loop terminates on) to 0, or when the size of the number the pointer is at is not equal to the size
        // of the list which it points to (When we aren't about to be out of bounds).
        while (pointers(index) == filteredLists(index).length && index > 0) {
          pointers(index) = 0
          pointers(index - 1) += 1
          index -= 1
        }
      }
    }

    validCombinations.toList.distinct
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
}
