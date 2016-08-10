package com.act.analysis.proteome.proteome_workflow

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.EcnumToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class EcnumToProteinPredictionFlow
  extends Workflow
  with EcnumToSequences
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert EC numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT = "a"
  private val OPTION_OUTPUT_HMM = "m"
  private val OPTION_RESULT_FILE = "o"
  private val OPTION_CLUSTAL_BINARIES = "c"
  private val OPTION_COMPARE_PROTEOME_LOCATION = "l"
  private val OPTION_DATABASE = "d"
  private val OPTION_EC_NUM = "e"
  private val OPTION_OUTPUT_FASTA_FILE = "f"
  private val OPTION_WORKING_DIRECTORY = "w"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_EC_NUM).
        required(true).
        hasArg.
        longOpt("ec-number")
        .desc("EC number to query against in format of X.X.X.X, " +
          "if you do not enter a fully defined sequence of four, " +
          "it will assume you want all reactions within a subgroup, " +
          "such as the value 6.1.1 will match 6.1.1.1 as well as 6.1.1.2"),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE).
        hasArg.
        longOpt("output-fasta-from-ecnum-location").
        desc("The file path to write the FASTA file " +
          "containing all the enzyme sequences that catalyze a reaction within the ecnum."),

      CliOption.builder(OPTION_ALIGNED_FASTA_FILE_OUTPUT).
        hasArg.
        longOpt("aligned-fasta-file-output-location").
        desc("The file path to write the FASTA file after alignment with CLUSTAL."),

      CliOption.builder(OPTION_OUTPUT_HMM).
        hasArg.
        longOpt("output-hmm-profile-location").
        desc("The file path to write the output HMM profile produced from the aligned FASTA."),

      CliOption.builder(OPTION_RESULT_FILE).
        hasArg.
        longOpt("results-file-location").
        desc("The file path to write the results of the HMM search with the created HMM on the supplied proteome"),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_CLUSTAL_BINARIES).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("The file path of the ClustalOmega binaries used in alignment.").
        required(true),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION).
        longOpt("proteome-location").
        hasArg.
        desc("The file path of the proteome file that the constructed HMM should be searched against").
        required(true),

      CliOption.builder(OPTION_DATABASE).
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
    // Align sequence so we can build an HMM
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val clustalBinaries = new File(cl.getOptionValue(OPTION_CLUSTAL_BINARIES))

    // Align sequence so we can build an HMM, needs to know where aligner binaries are
    verifyInputFile(clustalBinaries)
    ClustalOmegaWrapper.setBinariesLocation(clustalBinaries)

    val proteomeLocation = new File(cl.getOptionValue(OPTION_COMPARE_PROTEOME_LOCATION))
    verifyInputFile(proteomeLocation)


    // Grab the ec number
    val ec_num = cl.getOptionValue(OPTION_EC_NUM)


    // Setup all the constant paths here
    val outputFastaPath = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE,
      "EC_" + ec_num,
      "output.fasta",
      workingDir
    )

    val alignedFastaPath = defineOutputFilePath(
      cl,
      OPTION_ALIGNED_FASTA_FILE_OUTPUT,
      "EC_" + ec_num,
      "output.aligned.fasta",
      workingDir
    )

    val outputHmmPath = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_HMM,
      "EC_" + ec_num,
      "output.hmm",
      workingDir
    )

    val resultFilePath = defineOutputFilePath(
      cl,
      OPTION_RESULT_FILE,
      "EC_" + ec_num,
      "output.hmm.result",
      workingDir
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingEcnums(ec_num, outputFastaPath, cl.getOptionValue(OPTION_DATABASE)) _)
    headerJob.thenRun(ecNumberToFasta)

    // Align Fasta sequence
    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
    headerJob.thenRun(alignFastaSequences)

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
    headerJob.thenRun(buildHmmFromFasta)

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, proteomeLocation, resultFilePath)
    headerJob.thenRun(searchNewHmmAgainstPanProteome)

    headerJob
  }
}
