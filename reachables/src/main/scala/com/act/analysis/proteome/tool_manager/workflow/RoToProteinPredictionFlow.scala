package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.{HmmerResultSetOperations, WorkingDirectoryUtility}
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite.RoToSequences
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer


class RoToProteinPredictionFlow extends {
  val SET_LOCATION = "setLocation"
  val OPTION_OUTPUT_FASTA_FILE_PREFIX = "f"
  val OPTION_RESULT_FILE_PREFIX = "o"
  val OPTION_WORKING_DIRECTORY_PREFIX = "w"
  val OPTION_RO_ARG_PREFIX = "r"
}
  with Workflow
  with RoToSequences
  with HmmerResultSetOperations
  with WorkingDirectoryUtility {

  override val HELP_MESSAGE = "Workflow to convert RO numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX = "a"
  private val OPTION_OUTPUT_HMM_PREFIX = "m"
  private val OPTION_SET_UNION_PREFIX = "u"
  private val OPTION_SET_INTERSECTION_PREFIX = "i"
  private val OPTION_CLUSTAL_BINARIES_PREFIX = "c"


  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_RO_ARG_PREFIX).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro-values").desc("RO number that should be querying against."),

      CliOption.builder(OPTION_OUTPUT_FASTA_FILE_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc(s"Output FASTA sequence containing all the enzyme sequences that catalyze a reaction within the RO."),


      CliOption.builder(OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX).
        hasArg.
        longOpt("aligned-fasta-file-output-location").
        desc(s"Output FASTA file after being aligned."),

      CliOption.builder(OPTION_OUTPUT_HMM_PREFIX).
        hasArg.
        longOpt("output-hmm-profile-location").
        desc(s"Output HMM profile produced from the aligned FASTA."),

      CliOption.builder(OPTION_RESULT_FILE_PREFIX).
        hasArg.
        longOpt("results-file-location").
        desc(s"Output HMM search on pan proteome with the produced HMM"),

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
        desc("Set the location of where the ClustalOmega binaries are located at").
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
    ClustalOmegaWrapper.setBinariesLocation(cl.getOptionValue(OPTION_CLUSTAL_BINARIES_PREFIX))

    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    // Setup file pathing
    val workingDirectory = cl.getOptionValue(OPTION_WORKING_DIRECTORY_PREFIX, null)

    def defineFilePath(optionName: String, identifier: String, defaultValue: String): String = {
      // Spaces tend to be bad for file names
      val filteredIdentifier = identifier.replace(" ", "_")

      // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
      val fileNameHead = cl.getOptionValue(optionName, defaultValue)
      val fileName = s"${fileNameHead}_$filteredIdentifier"

      new File(workingDirectory, fileName).getAbsolutePath
    }

    // Making into a list will make it so that we just send the whole package to one job.
    // Keeping as individual options will cause individual runs.
    val ro_args = cl.getOptionValues(OPTION_RO_ARG_PREFIX).toList
    val setQuery = cl.hasOption(OPTION_SET_UNION_PREFIX) | cl.hasOption(OPTION_SET_INTERSECTION_PREFIX)

    /*
     This RO context actually takes two types, either a List[String]
     if we are processing a list of single RO values, or a List[List[String]] to keep the API consistent.
     Then, it just iterates over only a single roContext in roContexts, passing the List[String] as the entire context
      */
    val roContexts = if (setQuery) ro_args.asInstanceOf[List[String]] else List(ro_args.asInstanceOf[List[String]])

    // For use later by set compare if option is set.
    val resultFilesBuffer = ListBuffer[String]()

    for (roContext <- roContexts) {
      // Setup all the constant paths here
      val outputFastaPath = defineFilePath(
        OPTION_OUTPUT_FASTA_FILE_PREFIX,
        roContext.toString,
        "output.fasta"
      )

      val alignedFastaPath = defineFilePath(
        OPTION_ALIGNED_FASTA_FILE_OUTPUT_PREFIX,
        roContext.toString,
        "output.aligned.fasta"
      )

      val outputHmmPath = defineFilePath(
        OPTION_OUTPUT_HMM_PREFIX,
        roContext.toString,
        "output.hmm"
      )

      val resultFilePath = defineFilePath(
        OPTION_RESULT_FILE_PREFIX,
        roContext.toString,
        "output.hmm.result"
      )

      resultFilesBuffer.append(resultFilePath)

      // Create the FASTA file out of all the relevant sequences.
      val roToFastaContext = Map(
        OPTION_RO_ARG_PREFIX -> roContext,
        OPTION_OUTPUT_FASTA_FILE_PREFIX -> outputFastaPath
      )
      val roToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos, roToFastaContext)
      headerJob.thenRunAtPosition(roToFasta, 0)

      // Align Fasta sequence
      val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
      alignFastaSequences.writeOutputStreamToLogger()
      alignFastaSequences.writeErrorStreamToLogger()
      headerJob.thenRunAtPosition(alignFastaSequences, 1)

      // Build a new HMM
      val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
      buildHmmFromFasta.writeErrorStreamToLogger()
      buildHmmFromFasta.writeOutputStreamToLogger()
      headerJob.thenRunAtPosition(buildHmmFromFasta, 2)

      // Use the built HMM to find novel proteins
      val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, panProteomeLocation, resultFilePath)
      searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
      searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()
      headerJob.thenRunAtPosition(searchNewHmmAgainstPanProteome, 3)
    }

    // Run set operations
    val setContext = Map(
      OPTION_RESULT_FILE_PREFIX -> resultFilesBuffer.toList,
      SET_LOCATION -> new File(OPTION_RESULT_FILE_PREFIX).getParent,
      OPTION_RO_ARG_PREFIX -> ro_args.mkString(sep = "_")
    )

    if (cl.hasOption(OPTION_SET_UNION_PREFIX)) {
      // Context = All result files
      val setJob = ScalaJobWrapper.wrapScalaFunction(setUnionCompareOfHmmerSearchResults, setContext)
      headerJob.thenRun(setJob)
    }
    if (cl.hasOption(OPTION_SET_INTERSECTION_PREFIX)) {
      val setJob = ScalaJobWrapper.wrapScalaFunction(setIntersectionCompareOfHmmerSearchResults, setContext)
      headerJob.thenRun(setJob)
    }

    headerJob
  }
}
