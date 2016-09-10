package com.act.biointerpretation.rsmiles

import java.io.File

import com.act.workflow.tool_manager.jobs.{Job, ScalaJob}
import com.act.workflow.tool_manager.tool_wrappers.SparkWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.log4j.LogManager


class AbstractChemicalsToL3ProjectionWorkflow extends Workflow {

  private val LOGGER = LogManager.getLogger(getClass)

  val OPTION_DATABASE = "d"
  val OPTION_WORKING_DIRECTORY = "w"
  val OPTION_SUBSTRATE_COUNTS = "s"
  val OPTION_USE_CACHED_RESULTS = "c"
  val OPTION_SPARK_MASTER = "m"
  val OPTION_CHEMAXON_LICENSE = "l"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_DATABASE).
        hasArg.
        longOpt("database").
        desc("The database to connect to.  This is where we will find the abstract chemicals and reactions.  " +
          "By default uses the \"marvin\" database."),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        required.
        desc("The directory in which to run and create all intermediate files. This directory will be created if it " +
          "does not already exist."),

      CliOption.builder(OPTION_SUBSTRATE_COUNTS).
        hasArgs.
        valueSeparator(',').
        longOpt("substrate-counts").
        required.
        desc("A list of numbers.  This list will inform which reactions will be written to a file by " +
          "filtering the reactions by substrate.  For example, \"1,2\" would mean that 1 and 2 " +
          "substrate reactions will be written to a file."),

      CliOption.builder(OPTION_SPARK_MASTER).
        longOpt("spark-master").
        desc("Where to look for the spark master connection. " +
          "Uses \"spark://10.0.20.19:7077\" by default."),

      CliOption.builder(OPTION_USE_CACHED_RESULTS).
        longOpt("use-cached-results").
        desc("If this flag is enabled, we will check if the file that would be " +
          "made currently exists and use that file wherever possible."),

      CliOption.builder(OPTION_CHEMAXON_LICENSE).
        longOpt("chemaxon-license-file").
        hasArg.
        required.
        desc("Location of the \"license_Start-up.cxl\" file."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {
    // Make sure we have an assembled JAR available.
    val sparkMaster = cl.getOptionValue(OPTION_SPARK_MASTER, "spark://10.0.20.19:7077")
    headerJob.thenRun(SparkWrapper.sbtAssembly(useCached = false))

    val chemaxonLicense = new File(cl.getOptionValue(OPTION_CHEMAXON_LICENSE))
    require(chemaxonLicense.exists(), s"Chemaxon license does not exist as the supplied location.  " +
      s"File path supplied was ${chemaxonLicense.getAbsolutePath}")

    val outputDirectory = new File(cl.getOptionValue(OPTION_WORKING_DIRECTORY))
    require(!outputDirectory.isFile, "Working directory must be a directory, not a file.")
    if (!outputDirectory.exists()) outputDirectory.mkdirs()

    // Default marvin
    val database = cl.getOptionValue(OPTION_DATABASE, "marvin")

    val individualSubstrateFunction = AbstractChemicalsToReactions.calculateAbstractSubstrates(database)_

    val substrateCounts: List[Int] = cl.getOptionValues(OPTION_SUBSTRATE_COUNTS).map(_.toInt).toList
    val jobs = substrateCounts.map(count => {

      // Step 1: Abstract chemicals => Abstract reactions substrate list
      val substratesOutputFileName = s"FromDatabase$database.AbstractReactions$count.Substrates.txt"
      val reactionsOutputFileName = s"FromDatabase$database.AbstractReactions$count.txt"

      val substrateListOutputFile = new File(outputDirectory, substratesOutputFileName)
      val reactionListOutputFile = new File(outputDirectory, reactionsOutputFileName)

      val appliedFunction: () => Unit = individualSubstrateFunction(substrateListOutputFile, reactionListOutputFile, count)

      val abstractChemicalsToSubstrateListJob = if (cl.hasOption(OPTION_USE_CACHED_RESULTS) && substrateListOutputFile.exists()) {
        LOGGER.info(s"Using cached file ${substrateListOutputFile.getAbsolutePath}")
        new ScalaJob("Using cached substrate list", () => Unit)
      } else {
        new ScalaJob("Abstract chemicals to substrate list", appliedFunction)
      }

      // Step 2: Spark submit substrate list => RO projection
      val projectionDir = new File(outputDirectory, "ProjectionResults")
      if (!projectionDir.exists()) projectionDir.mkdirs()

      val roProjectionsOutputFileDirectory = new File(projectionDir, s"Substrates_${count}_db_${database}_AbstractReactionRoProjections")

      val roProjectionArgs = List(
        "--substrates-list", substrateListOutputFile.getAbsolutePath,
        "-o", roProjectionsOutputFileDirectory.getAbsolutePath,
        "-l", chemaxonLicense.getAbsolutePath
      )

      val singleSubstrateRoProjectorClassPath = "com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector"
      // Check if class path exists.
      Class.forName(singleSubstrateRoProjectorClassPath)

      val sparkRoProjection = SparkWrapper.runClassPath(
        singleSubstrateRoProjectorClassPath,
        sparkMaster,
        roProjectionArgs
      )

      abstractChemicalsToSubstrateListJob.thenRun(sparkRoProjection)

      // Step 3: Spark submit match projections to input reactions


      // Step 4: Construct SARs from matching reactions

      // Step 5: Project RO + SAR over L3







      abstractChemicalsToSubstrateListJob
    })

    headerJob.thenRunBatch(jobs)
  }
}
