package com.act.analysis.proteome.proteome_workflow

import com.act.workflow.tool_manager.jobs.{Job, ScalaJob}
import com.act.workflow.tool_manager.tool_wrappers.ScalaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.EcnumToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class EcnumToFastaFlow extends Workflow
  with EcnumToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert EC Numbers into an unaligned FASTA file."
  private val OPTION_EC_NUM_ARG_PREFIX = "e"
  private val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  private val OPTION_WORKING_DIRECTORY_PREFIX = "w"
  private val OPTION_DATABASE_PREFIX = "d"

  private val logger = LogManager.getLogger(getClass.getName)

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_EC_NUM_ARG_PREFIX).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ec-number").
        desc("The EC number to query against in format of X.X.X.X, " +
          "if you do not enter a fully defined sequence of four, " +
          "it will assume you want all reactions within a subgroup, " +
          "such as the value 6.1.1 will match 6.1.1.1 as well as 6.1.1.2"),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ecnum-location").
        desc("The file path to write the FASTA file " +
          "containing all the enzyme sequences that catalyze a reaction within the ecnum."),

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
    // Grab the ec number
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY_PREFIX, null)

    // Setup all the constant paths here
    def defineEcNumberJob(ecnum: String): ScalaJob = {
      val outputFastaPath = defineOutputFilePath(
        cl,
        OPTION_OUTPUT_FASTA_FILE_PREFIX,
        "EC_" + ecnum,
        "output.fasta",
        workingDir
      )

      // Create the FASTA file out of all the relevant sequences.
      ScalaJobWrapper.wrapScalaFunction(
        writeFastaFileFromEnzymesMatchingEcnums(ecnum, outputFastaPath, cl.getOptionValue(OPTION_DATABASE_PREFIX)) _)
    }

    headerJob.thenRunBatch(cl.getOptionValues(OPTION_EC_NUM_ARG_PREFIX).toList.map(defineEcNumberJob))

    headerJob
  }
}
