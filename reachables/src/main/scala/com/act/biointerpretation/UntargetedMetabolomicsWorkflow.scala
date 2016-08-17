package com.act.biointerpretation

import java.io.File
import java.util

import com.act.biointerpretation.l2expansion.L2ExpansionDriver
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.sarinference.LibMcsClustering
import com.act.jobs.JavaRunnable
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}

import scala.collection.JavaConverters._

class UntargetedMetabolomicsWorkflow extends Workflow with WorkingDirectoryUtility {

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_SUBSTRATES = "s"
  private val OPTION_RO_IDS = "r"
  private val OPTION_MASS_THRESHOLD = "m"
  private val OPTION_LCMS_POSITIVE_RATE = "p"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("The directory in which to run and create all intermediate files."),

      CliOption.builder(OPTION_SUBSTRATES).
        required(true).
        hasArg.
        longOpt("substrates").
        desc("A filepath to a file containing the substrate inchis on which to project the ROs."),

      CliOption.builder(OPTION_RO_IDS).
        required(true).
        hasArg.
        longOpt("ro_ids").
        desc("A filepath to a file containing the RO ids to use, one per line"),

      CliOption.builder(OPTION_MASS_THRESHOLD).
        required(false).
        hasArg.
        longOpt("mass-threshold").
        desc("The maximum mass of a substrate to be processed, in daltons."),

      CliOption.builder(OPTION_LCMS_POSITIVE_RATE).
        required(false).
        hasArg.
        longOpt("positive-rate").
        desc("The positive rate of the LCMS run, randomly assigned for now.").
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

    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)

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

  def buildFilesForRos(workingDir: String, fileName: String, roIds: List[Integer]): Map[Integer, File] = {
    Map() ++ roIds.map(r => (r, new File(workingDir, fileName + "." + r.toString)))
  }
}
