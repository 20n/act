package com.act.biointerpretation.networkanalysis

import java.io.File

import act.server.MongoDB
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._

/**
  * A Workflow to build a metabolism network from a set of prediction corpuses, and print out basic statistics on it.
  */
class NetworkBuilderFlow extends Workflow with WorkingDirectoryUtility with MongoWorkflowUtilities {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to run basic build of a network from input corpuses."

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_INPUT_CORPUSES = "i"
  private val OPTION_REACTION_ID_FILES = "r"
  private val OPTION_MONGO_DB = "d"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc(
          """The directory in which to run and create all intermediate files. This directory will be created if it
            |does not already exist.""".stripMargin).
        required(),

      CliOption.builder(OPTION_INPUT_CORPUSES).
        hasArgs.valueSeparator(',').
        desc(
          """The directories in which to find the input corpuses. The workflow will find all non-directory files that
            |are directly contained in any of the supplied directories, and try to use them as input files.""".stripMargin),

      CliOption.builder(OPTION_REACTION_ID_FILES).
        hasArgs.valueSeparator(',').
        desc("The files in which to find reaction IDs to load into the network."),

      CliOption.builder(OPTION_MONGO_DB).
        hasArg().
        desc(
          """The mongo DB from which to read any relevant reactions. Required
            |if the reaction ID files list is nonempty.""".stripMargin),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {

    val workingDirPath = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val workingDir: File = new File(workingDirPath)
    if (!workingDir.exists()) {
      workingDir.mkdir()
    }

    var inputCorpuses = List[File]()

    if (cl.hasOption(OPTION_INPUT_CORPUSES)) {
      val corpusDirs = cl.getOptionValues(OPTION_INPUT_CORPUSES).map(path => new File(path))

      def findInputFiles(directory: File): List[File] = {
        val inputFiles = directory.listFiles().toList.filter(!_.isDirectory)
        inputFiles.foreach(verifyInputFile(_))
        inputFiles
      }

      inputCorpuses = corpusDirs.flatMap(corpusDir => findInputFiles(corpusDir)).toList
    }

    var reactionFiles = List[File]()
    if (cl.hasOption(OPTION_REACTION_ID_FILES)) {
      reactionFiles = cl.getOptionValues(OPTION_REACTION_ID_FILES).toList.map(path => new File(path))
    }

    val outputFile = new File(workingDir, "networkOutput")
    verifyOutputFile(outputFile)

    var mongoDb: MongoDB = null
    if (reactionFiles.length > 0) {
      if (!cl.hasOption(OPTION_MONGO_DB)) {
        logger.error("Must supply mongo DB if reaction ID files are given.")
        exitWithHelp(getCommandLineOptions)
      }
      mongoDb = connectToMongoDatabase(cl.getOptionValue(OPTION_MONGO_DB))
    }

    val networkBuilder = new NetworkBuilder(inputCorpuses.asJava, reactionFiles.asJava, mongoDb, outputFile, true)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network builder", networkBuilder))

    val networkStats = new NetworkStats(outputFile);
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network stats", networkStats))
    headerJob
  }
}
