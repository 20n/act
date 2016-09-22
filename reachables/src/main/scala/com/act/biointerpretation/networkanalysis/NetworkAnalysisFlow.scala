package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

/**
  * A workflow to read in a metabolism network from file, optionally fill it out with LCMS data from a given input
  * file, and then print out basic statistics about it.
  */
class NetworkAnalysisFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to link a network with lcms results and/or get statistics about it."

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
        hasArg.
        desc("The file path to the input lcms results. If this option is used, the network's LCMS data is reset " +
          "based on the supplied input LCMS data.  If no path is provided, the input network is analyzed as is, " +
          "with or without LCMS results."),

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

    val inputNetworkFile = new File(cl.getOptionValue(OPTION_INPUT_NETWORK))
    verifyInputFile(inputNetworkFile)
    var networkToAnalyze = inputNetworkFile

    if (cl.hasOption(OPTION_INPUT_LCMS)) {
      val inputLcmsFile = new File(cl.getOptionValue(OPTION_INPUT_LCMS))
      verifyInputFile(inputLcmsFile)

      val outputFile = new File(workingDir, "network.withLcms")
      verifyOutputFile(outputFile)

      val networkLcmsLinker = new NetworkLcmsLinker(inputNetworkFile, inputLcmsFile, outputFile)
      headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network lcms linker", networkLcmsLinker))

      networkToAnalyze = outputFile
    }

    val networkStats = new NetworkStats(networkToAnalyze);
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network stats", networkStats))
    headerJob
  }
}
