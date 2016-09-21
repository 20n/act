package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class NetworkAnalysisFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to link a network with lcms results and get statistics on it."

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_INPUT_NETWORK = "i"
  private val OPTION_INPUT_LCMS = "l"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("The directory in which to run and create all intermediate files. This directory will be created if it " +
          "does not already exist.").
        required(),

      CliOption.builder(OPTION_INPUT_NETWORK).
        hasArg.
        desc("The file path to the input network.").
        required(),

      CliOption.builder(OPTION_INPUT_LCMS).
        desc("The file path to the input lcms results.").
        required(),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  // Implement this with the job structure you want to run to define a workflow
  override def defineWorkflow(cl: CommandLine): Job = {

    /**
      * Handle command line args and create files
      */
    val workingDirPath = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val workingDir: File = new File(workingDirPath)

    val inputNetworkFile = new File(cl.getOptionValue(OPTION_INPUT_NETWORK))
    val inputLcmsFile = new File(cl.getOptionValue(OPTION_INPUT_LCMS))
    val outputFile = new File(workingDir, "network.withLcms")

    verifyInputFile(inputNetworkFile)
    verifyInputFile(inputLcmsFile)
    verifyOutputFile(outputFile)

    val networkLcmsLinker = new NetworkLcmsLinker(inputNetworkFile, inputLcmsFile, outputFile)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network lcms linker", networkLcmsLinker))

    val networkStats = new NetworkStats(outputFile);
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network stats", networkStats))
    headerJob
  }
}
