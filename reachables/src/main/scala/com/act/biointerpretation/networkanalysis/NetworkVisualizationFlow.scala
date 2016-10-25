package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.biointerpretation.networkanalysis.GraphViz.{DotEdge, DotColor, PrecursorReportVisualizer}
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class NetworkVisualizationFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Reads in a metabolism network and writes out its GraphViz representation."

  private val OPTION_INPUT_NETWORK = "i"
  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_ORG_STRINGS = "s"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_INPUT_NETWORK).
        hasArg.
        desc("The file path to the input network.").
        required(),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        desc(
          """The directory in which to write the output files. There are two output files: the dot graph itself, and
            |an auxiliary TSV file identifying the inchis of each node in the network.""".stripMargin)
        .required(),

      CliOption.builder(OPTION_ORG_STRINGS).
        hasArg.
        desc(
          """One or more strings representing organisms of interest. Edges with orgs containing
            |these strings are colored in red.""".stripMargin),

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

    val workingDir = new File(cl.getOptionValue(OPTION_WORKING_DIRECTORY))
    createWorkingDirectory(workingDir)

    val networkViz = new PrecursorReportVisualizer()
    if (cl.hasOption(OPTION_ORG_STRINGS)) {
      for (x <- cl.getOptionValues(OPTION_ORG_STRINGS)) {
        networkViz.addOrgOfInterest(x, DotColor.RED)
      }
    }

    val runnableViz = networkViz.getRunner(inputNetworkFile, workingDir)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("graphViz", runnableViz))
    headerJob
  }
}
