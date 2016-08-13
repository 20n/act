package com.act.lcms.db.analysis

import java.io.File
import java.util
import java.util.Arrays

import com.act.lcms.db.io.DB
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.commons.lang3.StringUtils

import scala.collection.JavaConverters._

class IonMiningWorkflow extends Workflow with WorkingDirectoryUtility {

  private val OPTION_PLOTTING_DIR = "p"
  private val OPTION_INCLUDE_IONS = "i"
  private val OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE = "t"
  private val OPTION_LIST_OF_INCHIS_INPUT_FILE = "f"
  private val OPTION_INPUT_PREDICTION_CORPUS = "s"
  private val OPTION_LCMS_FILE_DIRECTORY = "d"
  private val OPTION_OUTPUT_PREFIX = "o"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_PLOTTING_DIR).
        required(true).
        hasArg.
        longOpt("plotting-directory").
        desc("The absolute path of the plotting directory"),

      CliOption.builder(OPTION_INCLUDE_IONS).
        hasArg.
        longOpt("ion-list").
        valueSeparator(',').
        desc("A comma-separated list of ions to include in the search (ions not in this list will be ignored)"),

      CliOption.builder(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE).
        required(true).
        hasArg.
        longOpt("wells-config").
        desc("A tsv file containing positive and negative wells"),

      CliOption.builder(OPTION_LIST_OF_INCHIS_INPUT_FILE).
        required(true).
        hasArg.
        longOpt("file-input-type").
        desc("If this option is specified, the input corpus is a list of inchis"),

      CliOption.builder(OPTION_INPUT_PREDICTION_CORPUS).
        required(true).
        hasArg.
        longOpt("prediction-corpus").
        desc("The input prediction corpus").
        required(),

      CliOption.builder(OPTION_LCMS_FILE_DIRECTORY).
        required(true).
        hasArg.
        longOpt("lcms-dir").
        desc("The lcms directory").
        required(),

      CliOption.builder(OPTION_OUTPUT_PREFIX).
        required(true).
        hasArg.
        longOpt("output-prefix").
        desc("The output prefix to write to").
        required(),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  // Implement this with the job structure you want to run to define a workflow
  override def defineWorkflow(cl: CommandLine): Job = {

    val lcmsDir = cl.getOptionValue(OPTION_LCMS_FILE_DIRECTORY)
    val db: DB = DB.openDBFromCLI(cl)
    val inputPredictionCorpus = cl.getOptionValue(OPTION_INPUT_PREDICTION_CORPUS)
    val listOfInchisInputFile = cl.getOptionValue(OPTION_LIST_OF_INCHIS_INPUT_FILE)
    val outputPrefix = cl.getOptionValue(OPTION_OUTPUT_PREFIX)
    val includeIons:util.Set[String] = cl.getOptionValues(OPTION_INCLUDE_IONS).toSet
    val plottingDir = cl.getOptionValue(OPTION_PLOTTING_DIR)
    val experimentalSetup = cl.getOptionValue(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE)

    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction(
      IonDetectionAnalysis.getRunnableIonDetectionAnalysis(db, inputPredictionCorpus, experimentalSetup, false, includeIons, lcmsDir, plottingDir, outputPrefix)));


    val substratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATES))
    val roIdFile = new File(cl.getOptionValue(OPTION_RO_IDS))
    verifyInputFile(substratesFile)
    verifyInputFile(roIdFile)

    val erosCorpus = new ErosCorpus()
    erosCorpus.loadValidationCorpus()
    val roIds = erosCorpus.getRoIdListFromFile(roIdFile).asScala

    val predictionsFilename = "predictions"
    val predictionsFiles = buildFilesForRos(workingDir, predictionsFilename, roIds.toList)

    val sarTreeFilename = "sartree"
    val sarTreeFiles = buildFilesForRos(workingDir, sarTreeFilename, roIds.toList)

    val scoredSarsFilename = "scored_sars"
    val scoredSarsFiles = buildFilesForRos(workingDir, scoredSarsFilename, roIds.toList)

    val lcmsFilename = "lcms_positives"
    val lcmsFile = new File(workingDir, lcmsFilename)

    var maxMass = Integer.MAX_VALUE
    if (cl.hasOption(OPTION_MASS_THRESHOLD)) {
      maxMass = Integer.parseInt(cl.getOptionValue(OPTION_MASS_THRESHOLD))
    }

    val positiveRate = cl.getOptionValue(OPTION_LCMS_POSITIVE_RATE).toDouble;

    // Build one job per RO for L2 expansion
    val singleThreadExpansionJobs =
      roIds.map(roId =>
        JavaJobWrapper.wrapJavaFunction(
          L2ExpansionDriver.getRunnableOneSubstrateRoExpander(
            util.Arrays.asList(roId),
            substratesFile,
            predictionsFiles.get(roId).get,
            maxMass)))
    // Run one job per RO for L2 expansion
    headerJob.thenRunBatch(singleThreadExpansionJobs.toList)

    // TODO: when Michael adds the capability, change this workflow to run the clustering jobs and LCMS job in parallel
    // Build one job per RO for clustering
    val clusteringJobs =
      roIds.map(roId =>
        JavaJobWrapper.wrapJavaFunction(LibMcsClustering.getRunnableClusterer(
          predictionsFiles.get(roId).get,
          sarTreeFiles.get(roId).get)))
    // Run one job per RO for clustering
    headerJob.thenRunBatch(clusteringJobs.toList)

    // TODO: when Vijay's LCMS code is ready, replace this with the real thing
    // Build a dummy LCMS job that doesn't do anything.
    val lcmsJob = JavaJobWrapper.wrapJavaFunction(
      new JavaRunnable {
        override def run(): Unit = {
          print("Creating empty LCMS results file: Didn't implement LCMS yes.")
        }

        override def toString(): String = {
          "DUMMY_LCMS_JOB"
        }
      }
    )
    headerJob.thenRun(lcmsJob)

    // Build one job per RO for scoring, using a random LCMS hit selector instead of actual data.
    val scoringJobs = roIds.map(roId =>
      JavaJobWrapper.wrapJavaFunction(
        LibMcsClustering.getRunnableRandomSarScorer(
          sarTreeFiles.get(roId).get,
          scoredSarsFiles.get(roId).get,
          positiveRate)
      )
    )
    // Run one job per RO for scoring
    headerJob.thenRunBatch(scoringJobs.toList)

    headerJob
  }
}

