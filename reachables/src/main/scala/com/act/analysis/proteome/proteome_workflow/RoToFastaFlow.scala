package com.act.analysis.proteome.proteome_workflow

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.ScalaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.RoToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class RoToFastaFlow
  extends Workflow
  with RoToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO number into a FASTA file."
  private val OPTION_DATABASE_PREFIX = "d"
  private val logger = LogManager.getLogger(getClass.getName)
  private val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  private val OPTION_WORKING_DIRECTORY_PREFIX = "w"
  private val OPTION_RO_ARG_PREFIX = "r"

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
        desc("The file path to write the output FASTA file containing" +
          " all the enzyme sequences that catalyze a reaction within the RO."),

      CliOption.builder(OPTION_WORKING_DIRECTORY_PREFIX).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_DATABASE_PREFIX).
        longOpt("database").
        hasArg.desc("The name of the MongoDB to use for this query.").
        required(true),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )
    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def defineWorkflow(cl: CommandLine): Job = {
    val ro = cl.getOptionValue(OPTION_RO_ARG_PREFIX)
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY_PREFIX, null)

    // Setup all the constant paths here
    val outputFastaPath = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE_PREFIX,
      "RO_" + ro,
      "output.fasta",
      workingDir
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos(List(ro), outputFastaPath, cl.getOptionValue(OPTION_DATABASE_PREFIX)) _)
    headerJob.thenRun(ecNumberToFasta)

    headerJob
  }

}
