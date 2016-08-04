package com.act.analysis.proteome.proteome_workflow

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.{HmmerResultSetOperations, WorkingDirectoryUtility}
import com.act.workflow.tool_manager.workflow.workflow_mixins.composite.RoToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer


class RoToProteinPredictionFlow
  extends Workflow
  with RoToSequences
  with HmmerResultSetOperations
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  private val OPTION_RESULT_FILE_PREFIX = "o"
  private val OPTION_WORKING_DIRECTORY_PREFIX = "w"
  private val OPTION_RO_ARG_PREFIX = "r"
  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX = "a"
  private val OPTION_OUTPUT_HMM_PREFIX = "m"
  private val OPTION_SET_UNION_PREFIX = "u"
  private val OPTION_SET_INTERSECTION_PREFIX = "i"
  private val OPTION_CLUSTAL_BINARIES_PREFIX = "c"
  private val OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX = "l"


  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_RO_ARG_PREFIX).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro-values").
        desc(s"The RO (Reaction Operator) numbers that should be used to construct the workflow.  " +
          s"Multiple operators will be searched as a group unless $OPTION_SET_INTERSECTION_PREFIX " +
          s"or $OPTION_SET_UNION_PREFIX are enabled, in which case each RO will " +
          s"be evaluated individually and the set operations performed on the group."),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc("The file path to write the FASTA file " +
          "containing all the enzyme sequences that catalyze a reaction within the ecnum."),

      CliOption.builder(OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX).
        hasArg.
        longOpt("aligned-fasta-file-output-location").
        desc("The file path to write the FASTA file after alignment with CLUSTAL."),

      CliOption.builder(OPTION_OUTPUT_HMM_PREFIX).
        hasArg.
        longOpt("output-hmm-profile-location").
        desc("The file path to write the output HMM profile produced from the aligned FASTA."),

      CliOption.builder(OPTION_RESULT_FILE_PREFIX).
        hasArg.
        longOpt("results-file-location").
        desc("The file path to write the results of the HMM search with the created HMM on the supplied proteome"),

      CliOption.builder(OPTION_WORKING_DIRECTORY_PREFIX).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_SET_UNION_PREFIX).
        longOpt("obtain-set-union-results").
        desc("Run all ROs as individual runs, and then do a set union on all the output proteins."),

      CliOption.builder(OPTION_SET_INTERSECTION_PREFIX).
        longOpt("obtain-set-intersection-results").
        desc("Run all ROs as individual runs, then do a set intersection on all the output proteins."),

      CliOption.builder(OPTION_CLUSTAL_BINARIES_PREFIX).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("The file path of the ClustalOmega binaries used in alignment.").
        required(true),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX).
        longOpt("proteome-location").
        hasArg.
        desc("The file path of the proteome file that the constructed HMM should be searched against").
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
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY_PREFIX, null)

    // Align sequence so we can build an HMM, needs to know where aligner binaries are
    if (!verifyInputFilePath(cl.getOptionValue(OPTION_CLUSTAL_BINARIES_PREFIX))) {
      throw new RuntimeException(s"Clustal binary path was not valid. " +
        s"Given path was ${cl.getOptionValue(OPTION_CLUSTAL_BINARIES_PREFIX)}")
    }

    ClustalOmegaWrapper.setBinariesLocation(cl.getOptionValue(OPTION_CLUSTAL_BINARIES_PREFIX))
    val proteomeLocation = cl.getOptionValue(OPTION_COMPARE_PROTEOME_LOCATION_ARG_PREFIX)

    if (!verifyInputFilePath(proteomeLocation)) {
      throw new RuntimeException(s"Proteome file location was not valid.  Given input was $proteomeLocation.")
    }

    // Making into a list will make it so that we just send the whole package to one job.
    // Keeping as individual options will cause individual runs.
    val ro_args: List[String] = cl.getOptionValues(OPTION_RO_ARG_PREFIX).toList
    val setQuery = cl.hasOption(OPTION_SET_UNION_PREFIX) | cl.hasOption(OPTION_SET_INTERSECTION_PREFIX)

    /*
     This RO context actually takes two types, either a List[String]
     if we are processing a list of single RO values, or a List[List[String]] to keep the API consistent.
     Then, it just iterates over only a single roContext in roContexts,
     passing the List[String] as the entire context
    */

    val roContexts: List[List[String]] = if (setQuery) ro_args.map(List(_)) else List(ro_args)

    // For use later by set compare if option is set.
    val resultFilesBuffer = ListBuffer[String]()

    for (roContext <- roContexts) {
      // Setup all the constant paths here
      val outputFastaPath = defineOutputFilePath(
        cl,
        OPTION_OUTPUT_FASTA_FILE_PREFIX,
        roContext.toString,
        "output.fasta",
        workingDir
      )

      val alignedFastaPath = defineOutputFilePath(
        cl,
        OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX,
        roContext.toString,
        "output.aligned.fasta",
        workingDir
      )

      val outputHmmPath = defineOutputFilePath(
        cl,
        OPTION_OUTPUT_HMM_PREFIX,
        roContext.toString,
        "output.hmm",
        workingDir
      )

      val resultFilePath = defineOutputFilePath(
        cl,
        OPTION_RESULT_FILE_PREFIX,
        roContext.toString,
        "output.hmm.result",
        workingDir
      )

      resultFilesBuffer.append(resultFilePath)

      // Create the FASTA file out of all the relevant sequences.
      val roToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos(roContext, outputFastaPath) _)
      headerJob.thenRunAtPosition(roToFasta, 0)

      // Align Fasta sequence
      val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
      headerJob.thenRunAtPosition(alignFastaSequences, 1)

      // Build a new HMM
      val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
      headerJob.thenRunAtPosition(buildHmmFromFasta, 2)

      // Use the built HMM to find novel proteins
      val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, proteomeLocation, resultFilePath)
      headerJob.thenRunAtPosition(searchNewHmmAgainstPanProteome, 3)
    }


    val resultFileList = resultFilesBuffer.toList
    val setResultFileDirectory = new File(OPTION_RESULT_FILE_PREFIX).getParent
    val roFileNameUniqueId = ro_args.mkString(sep = "_")

    if (cl.hasOption(OPTION_SET_UNION_PREFIX)) {
      val setJob = ScalaJobWrapper.wrapScalaFunction(
        setUnionHmmerSearchResults(resultFileList, setResultFileDirectory, roFileNameUniqueId) _
      )

      headerJob.thenRun(setJob)
    }
    if (cl.hasOption(OPTION_SET_INTERSECTION_PREFIX)) {
      val setJob = ScalaJobWrapper.wrapScalaFunction(
        setIntersectHmmerSearchResults(resultFileList, setResultFileDirectory, roFileNameUniqueId) _
      )

      headerJob.thenRun(setJob)
    }

    headerJob
  }
}
