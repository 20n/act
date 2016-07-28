package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.ScalaJobWrapper
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite.EcnumToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class EcnumToFastaFlow extends {
  val OPTION_EC_NUM_ARG_PREFIX = "e"
  val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  val OPTION_WORKING_DIRECTORY_PREFIX = "w"
}
  with Workflow
  with EcnumToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert EC Numbers into an unaligned FASTA file."
  private val logger = LogManager.getLogger(getClass.getName)

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_EC_NUM_ARG_PREFIX).
        required(true).
        hasArg.
        longOpt("ec-number")
        .desc("EC number to query against in format of X.X.X.X, " +
          "if you do not enter a fully defined sequence of four, " +
          "it will assume you want all reactions within a subgroup, " +
          "such as the value 6.1.1 will match 6.1.1.1 as well as 6.1.1.2"),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ecnum-location").
        desc(s"Output FASTA sequence containing all the enzyme sequences that catalyze a reaction within the ecnum."),

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
    val ec_num = cl.getOptionValue(OPTION_EC_NUM_ARG_PREFIX)

    // Setup all the constant paths here
    val outputFastaPath = defineFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE_PREFIX,
      "EC_" + ec_num,
      "output.fasta"
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFastaContext = Map(
      OPTION_EC_NUM_ARG_PREFIX -> ec_num,
      OPTION_OUTPUT_FASTA_FILE_PREFIX -> outputFastaPath
    )
    val ecNumberToFasta =
      ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingEcnums, ecNumberToFastaContext)
    headerJob.thenRun(ecNumberToFasta)

    headerJob
  }
}
