package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite.EcnumToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class EcnumToProteinPredictionFlow extends {
  val OPTION_EC_NUM_ARG_PREFIX = "e"
  val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  val OPTION_WORKING_DIRECTORY_PREFIX = "w"
}
  with Workflow
  with EcnumToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert EC numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX = "a"
  private val OPTION_OUTPUT_HMM_ARG_PREFIX = "m"
  private val OPTION_RESULT_FILE_ARG_PREFIX = "o"
  private val OPTION_CLUSTAL_BINARIES_ARG_PREFIX = "c"
  private val OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX = "l"

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

      CliOption.builder(OPTION_ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX).
        hasArg.
        longOpt("aligned-fasta-file-output-location").
        desc(s"Output FASTA file after being aligned."),

      CliOption.builder(OPTION_OUTPUT_HMM_ARG_PREFIX).
        hasArg.
        longOpt("output-hmm-profile-location").
        desc(s"Output HMM profile produced from the aligned FASTA."),

      CliOption.builder(OPTION_RESULT_FILE_ARG_PREFIX).
        hasArg.
        longOpt("results-file-location").
        desc(s"Output HMM search on pan proteome with the produced HMM"),

      CliOption.builder(OPTION_WORKING_DIRECTORY_PREFIX).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_CLUSTAL_BINARIES_ARG_PREFIX).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("Set the location of where the ClustalOmega binaries are located at").
        required(true),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX).
        longOpt("proteome-location").
        hasArg.
        desc("Location of the proteome file that the constructed HMM should be searched against").
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
    logger.info("Finished processing command line information")
    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation(cl.getOptionValue(OPTION_CLUSTAL_BINARIES_ARG_PREFIX))
    val proteomeLocation = cl.getOptionValue(OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX)

    // Grab the ec number
    val ec_num = cl.getOptionValue(OPTION_EC_NUM_ARG_PREFIX)


    // Setup all the constant paths here
    val outputFastaPath = defineFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE_PREFIX,
      "EC_" + ec_num,
      "output.fasta"
    )

    val alignedFastaPath = defineFilePath(
      cl,
      OPTION_ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX,
      "EC_" + ec_num,
      "output.aligned.fasta"
    )

    val outputHmmPath = defineFilePath(
      cl,
      OPTION_OUTPUT_HMM_ARG_PREFIX,
      "EC_" + ec_num,
      "output.hmm"
    )

    val resultFilePath = defineFilePath(
      cl,
      OPTION_RESULT_FILE_ARG_PREFIX,
      "EC_" + ec_num,
      "output.hmm.result"
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFastaContext = Map(
      OPTION_EC_NUM_ARG_PREFIX -> ec_num,
      OPTION_OUTPUT_FASTA_FILE_PREFIX -> outputFastaPath
    )
    val ecNumberToFasta =
      ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingEcnums, ecNumberToFastaContext)
    headerJob.thenRun(ecNumberToFasta)

    // Align Fasta sequence
    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
    alignFastaSequences.writeOutputStreamToLogger()
    alignFastaSequences.writeErrorStreamToLogger()
    headerJob.thenRun(alignFastaSequences)

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
    buildHmmFromFasta.writeErrorStreamToLogger()
    buildHmmFromFasta.writeOutputStreamToLogger()
    headerJob.thenRun(buildHmmFromFasta)

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, proteomeLocation, resultFilePath)
    searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
    searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()
    headerJob.thenRun(searchNewHmmAgainstPanProteome)

    headerJob
  }
}
