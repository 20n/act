package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class NetworkVisualizationFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to read in a network and write out its GraphViz representation."

  private val OPTION_INPUT_NETWORK = "i"
  private val OPTION_OUTPUT_PATH = "o"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_INPUT_NETWORK).
        hasArg.
        desc("The file path to the input network.").
        required(),

      CliOption.builder(OPTION_OUTPUT_PATH).
        hasArg.
        desc("The path to write the output").
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

    val inputNetworkFile = new File(cl.getOptionValue(OPTION_INPUT_NETWORK))
    verifyInputFile(inputNetworkFile)

    val outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH))
    verifyOutputFile(outputFile)

    val networkViz = new PrecursorVizBuilder(inputNetworkFile, outputFile)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("graphViz", networkViz))
    headerJob
  }
}
