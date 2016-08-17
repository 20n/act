package com.act.analysis.proteome.proteome_workflow

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ClustalOmegaWrapper, ScalaJobWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.RoToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class RoToAlignedFastaHumanSpecificFlow extends Workflow with RoToSequences with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO number into a FASTA file with only human sequences."
  override val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_DATABASE = "d"
  private val OPTION_OUTPUT_FASTA_FILE = "f"
  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_RO_ARG = "r"
  private val OPTION_CLUSTAL_BINARIES = "c"
  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT = "a"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_RO_ARG).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro-values").
        desc("RO number that should be querying against."),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc("The file path to write the output FASTA file containing" +
          " all the enzyme sequences that catalyze a reaction within the RO."),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_DATABASE).
        longOpt("database").
        hasArg.desc("The name of the MongoDB to use for this query.").
        required(true),

      CliOption.builder(OPTION_CLUSTAL_BINARIES).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("The file path of the ClustalOmega binaries used in alignment.").
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
    val ro = cl.getOptionValue(OPTION_RO_ARG)
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val clustalBinaries = new File(cl.getOptionValue(OPTION_CLUSTAL_BINARIES))

    // Setup all the constant paths here
    val outputFastaPath = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE,
      "Human_RO_" + ro,
      "output.fasta",
      workingDir
    )

    val alignedFastaPath = defineOutputFilePath(
      cl,
      OPTION_ALIGNED_FASTA_FILE_OUTPUT,
      "Human_RO_" + ro,
      "output.aligned.fasta",
      workingDir
    )

    verifyInputFile(clustalBinaries)
    ClustalOmegaWrapper.setBinariesLocation(clustalBinaries)

    // Create the FASTA file out of all the relevant sequences.
    val roToFasta = ScalaJobWrapper.wrapScalaFunction(s"Write Fasta From RO, RO=$ro",
      writeFastaFileFromEnzymesMatchingRos(List(ro), outputFastaPath,
        cl.getOptionValue(OPTION_DATABASE), Option(".*sapiens.*")) _)
    headerJob.thenRun(roToFasta)

    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
    roToFasta.thenRun(alignFastaSequences)

    headerJob
  }

}


