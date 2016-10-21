package com.act.analysis.proteome.proteome_workflow

import java.io.File

import com.act.analysis.chemicals.ChemicalSimilarity
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ClustalOmegaWrapper, ScalaJobWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.{RoToSequences, SarTreeConstructor}
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.dmg.pmml.Tanimoto

class OrganismBasedRoToLcmsScoring extends Workflow with RoToSequences with SarTreeConstructor with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO number into a FASTA file with only a given organism's sequences.  " +
    "Then, we do sequence clustering and rank an InChI corpus against the " +
    "SeqSars that were created from that organism's sequence."

  private val OPTION_DATABASE = "d"
  private val OPTION_OUTPUT_FASTA_FILE = "f"
  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_RO_ARG = "r"
  private val OPTION_CLUSTAL_BINARIES = "c"
  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT = "a"
  private val OPTION_L2_PREDICTION_CORPUS_TO_SCORE = "l"
  private val OPTION_FORCE = "f"
  private val OPTION_OUTPUT_TSV = "t"
  private val OPTION_ORGANISM_REGEX = "o"
  private val OPTION_CHEMICAL_SIMILARITY_STRING = "s"

  override def getCommandLineOptions: Options = {

    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_RO_ARG).
        required(true).
        hasArg.
        longOpt("ro-value").
        desc("RO number that should be querying against."),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc("The file path to write the output FASTA file containing" +
          " all the enzyme sequences that catalyze a reaction within the RO."),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all lcms from a working directory you designate."),

      CliOption.builder(OPTION_DATABASE).
        longOpt("database").
        hasArg.desc("The name of the MongoDB to use for this query.").
        required(true),

      CliOption.builder(OPTION_CLUSTAL_BINARIES).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("The file path of the ClustalOmega binaries used in alignment.").
        required(true),

      CliOption.builder(OPTION_L2_PREDICTION_CORPUS_TO_SCORE).
        longOpt("l2-prediction-corpus").
        hasArg.
        desc("The previously serialized L2 Prediction Corpus that will be scored.").
        required(true),

      CliOption.builder(OPTION_FORCE).
        longOpt("force").
        desc("Overwrite any previous lcms.  Disables use of previously created lcms."),

      CliOption.builder(OPTION_OUTPUT_TSV).
        longOpt("output-tsv").
        desc("The output tsv to write the scorings to."),

      CliOption.builder(OPTION_ORGANISM_REGEX).
        longOpt("org-regex").
        hasArg.
        desc("Part of all of an organism name. Automatically flanked by .* to be flexible." +
          "If this is not provided the default is to match to humans."),

      CliOption.builder(OPTION_CHEMICAL_SIMILARITY_STRING).
        longOpt("similarity-method").
        hasArg.
        desc("A string which is provided to Chemaxon's chemical " +
          "SimilarityCalculatorFactory which creates calculators for molecule comparison.  " +
          "A complete documentation can be found at https://docs.chemaxon.com/display/docs/Similarity+search. " +
          "However, the basic options are Tanimoto,Tversky,Substructure,Superstructure, and Euclidean.  " +
          "Different parameters can be tuned and also provided as the input string, " +
          "which can be found at the previously mentioned web link."),

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
    val inchiFile = new File(cl.getOptionValue(OPTION_L2_PREDICTION_CORPUS_TO_SCORE))

    val organismName = cl.getOptionValue(OPTION_ORGANISM_REGEX, "sapiens")
    val orgRegex = Option(s".*$organismName.*")

    // Setup all the constant paths here
    val outputFastaPath = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_FASTA_FILE,
      s"RO_$ro.ORG_$organismName",
      "output.fasta",
      workingDir
    )

    val alignedFastaPath: File = defineOutputFilePath(
      cl,
      OPTION_ALIGNED_FASTA_FILE_OUTPUT,
      s"RO_$ro.ORG_$organismName",
      "output.aligned.fasta",
      workingDir
    )

    val outputFile: File = defineOutputFilePath(
      cl,
      OPTION_OUTPUT_TSV,
      s"RO_$ro.ORG_$organismName.${inchiFile.getName}",
      "BioRankingOutput",
      workingDir,
      fileEnding = "tsv"
    )

    // Initialize similarity here so we only do it and the user can provide averages if they'd like
    if (cl.hasOption(OPTION_CHEMICAL_SIMILARITY_STRING)){
      ChemicalSimilarity.init(cl.getOptionValue(OPTION_CHEMICAL_SIMILARITY_STRING))
    } else {
      ChemicalSimilarity.init()
    }

    // Cache previous alignments
    if (cl.hasOption(OPTION_FORCE) || !alignedFastaPath.exists) {
      verifyInputFile(clustalBinaries)
      ClustalOmegaWrapper.setBinariesLocation(clustalBinaries)

      // Create the FASTA file out of all the relevant sequences.
      val roToFasta = ScalaJobWrapper.wrapScalaFunction(s"Write Fasta From RO, RO=$ro",
        writeFastaFileFromEnzymesMatchingRos(List(ro), outputFastaPath, cl.getOptionValue(OPTION_DATABASE), organism = orgRegex) _
      )
      headerJob.thenRun(roToFasta)

      val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
      headerJob.thenRun(alignFastaSequences)
    }

    val sarTrees =
      ScalaJobWrapper.wrapScalaFunction("Construct SAR Trees",
        constructSarTreesFromAlignedFasta(alignedFastaPath, inchiFile, outputFile) _
      )
    headerJob.thenRun(sarTrees)

    headerJob
  }

}


