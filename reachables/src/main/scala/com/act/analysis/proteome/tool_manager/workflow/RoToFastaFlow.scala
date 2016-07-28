package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.ScalaJobWrapper
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite.RoToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class RoToFastaFlow extends {
  val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  val OPTION_WORKING_DIRECTORY_PREFIX = "w"
  val OPTION_RO_ARG_PREFIX = "r"
}
  with Workflow
  with RoToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO number into a FASTA file."
  private val logger = LogManager.getLogger(getClass.getName)

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_RO_ARG_PREFIX).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro-values").
        desc("RO number that should be querying against."),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc(s"Output FASTA sequence containing all the enzyme sequences that catalyze a reaction within the RO."),

      CliOption.builder(OPTION_WORKING_DIRECTORY_PREFIX).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )
    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def defineWorkflow(cl: CommandLine): Job = {
    logger.info("Finished processing command line information")

    // Grab the ec number
    val ro = cl.getOptionValue(OPTION_RO_ARG_PREFIX)

    // Setup all the constant paths here
    val outputFastaPath = defineFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE_PREFIX,
      "RO_" + ro,
      "output.fasta"
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFastaContext = Map(
      OPTION_RO_ARG_PREFIX -> ro,
      OPTION_OUTPUT_FASTA_FILE_PREFIX -> outputFastaPath
    )
    val ecNumberToFasta =
      ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos, ecNumberToFastaContext)
    headerJob.thenRun(ecNumberToFasta)

    headerJob
  }

}
