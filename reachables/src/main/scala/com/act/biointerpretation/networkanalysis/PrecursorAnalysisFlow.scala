package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._

/**
  * Read in a metabolism network from file, and do precursor calculations over it with respect to a set
  * of target inchis.
  */
class PrecursorAnalysisFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow for precursor analyses."

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_INPUT_NETWORK = "i"
  private val OPTION_TARGET_INCHIS = "t"
  private val OPTION_NUM_STEPS = "n"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc(
          """The directory in which to run and create all intermediate files. This directory will be created if it
            |does not already exist.""".stripMargin).
        required,

      CliOption.builder(OPTION_INPUT_NETWORK).
        hasArg.
        desc("The file path to the input network.").
        required,

      CliOption.builder(OPTION_TARGET_INCHIS).
        hasArgs().valueSeparator('|').
        desc("The target inchis to find precursors for.")
        required,

      CliOption.builder(OPTION_NUM_STEPS).
        hasArg().
        desc("The number of levels of precursors to return").
        required,

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {

    val workingDirPath = cl.getOptionValue(OPTION_WORKING_DIRECTORY)
    val workingDir: File = new File(workingDirPath)
    if (!workingDir.exists()) {
      workingDir.mkdir()
    }

    val inputNetworkFile = new File(cl.getOptionValue(OPTION_INPUT_NETWORK))
    verifyInputFile(inputNetworkFile)

    val numSteps = Integer.parseInt(cl.getOptionValue(OPTION_NUM_STEPS))

    val precursorAnalysis = new PrecursorAnalysis(
      inputNetworkFile, cl.getOptionValues(OPTION_TARGET_INCHIS).toList.asJava, numSteps, workingDir)

    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("PrecursorAnalysis", precursorAnalysis))
    headerJob
  }
}
