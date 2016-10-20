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
  * Builds a metabolism network from reactions and/or predictions and/or a previous network.
  */
class NetworkBuilderFlow extends Workflow with WorkingDirectoryUtility with MongoWorkflowUtilities {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE =
    """Build a metabolism network. A previously built network can be used as a starting point. From there, edges
      |can be added from the reactions in a DB, or from predictions in a PredictionCorpus.""".stripMargin

  private val OPTION_OUTPUT_FILE = "o"
  private val OPTION_BASE_NETWORK = "b"
  private val OPTION_INPUT_CORPUSES = "i"
  private val OPTION_MONGO_DB = "d"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_OUTPUT_FILE).
        hasArg.
        longOpt("output file path").
        desc("The path to which to write the output network.").
        required(),

      CliOption.builder(OPTION_BASE_NETWORK).
        hasArg.
        desc("A file containing a metabolism network to use as the base of the new network."),

      CliOption.builder(OPTION_INPUT_CORPUSES).
        hasArgs.valueSeparator(',').
        desc(
          """The directories in which to find the input corpuses. The workflow will find all non-directory
            |files that are directly contained in any of the supplied directories, and try to use them
            |as input files.""".stripMargin),

      CliOption.builder(OPTION_MONGO_DB).
        hasArg().
        desc("The mongo DB from which to read reactions. All reactions from the given DB are loaded."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {

    var baseNetwork: File = null
    if (cl.hasOption(OPTION_BASE_NETWORK)) {
      baseNetwork = new File(cl.getOptionValue(OPTION_BASE_NETWORK))
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

    val outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE))
    verifyOutputFile(outputFile)

    var mongoDb: MongoDB = null
    if (cl.hasOption(OPTION_MONGO_DB)) {
      mongoDb = connectToMongoDatabase(cl.getOptionValue(OPTION_MONGO_DB))
    }

    val networkBuilder =
      new NetworkBuilder(baseNetwork, inputCorpuses.asJava, mongoDb, outputFile, true)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network builder", networkBuilder))

    headerJob
  }
}
