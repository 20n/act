package com.act.biointerpretation

import java.io.File

import com.act.biointerpretation.l2expansion.{L2ExpansionDriver, L2InchiCorpus}
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
    val directory: File = new File(workingDir)
    directory.mkdirs()

    val rawSubstratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATES))
    verifyInputFile(rawSubstratesFile)

    val roIdFile = new File(cl.getOptionValue(OPTION_RO_IDS))
    verifyInputFile(roIdFile)

    val erosCorpus = new ErosCorpus()
    erosCorpus.loadValidationCorpus()
    val roIds = erosCorpus.getRoIdListFromFile(roIdFile).asScala.toList

    val filteredSubstratesFile = new File(workingDir, "filtered_substrates")

    val predictionsFilename = "predictions"
    val predictionsFiles = buildFilesForRos(workingDir, predictionsFilename, roIds)

    val sarTreeFilename = "sartree"
    val sarTreeFiles = buildFilesForRos(workingDir, sarTreeFilename, roIds)

    val scoredSarsFilename = "scored_sars"
    val scoredSarsFiles = buildFilesForRos(workingDir, scoredSarsFilename, roIds)

    val lcmsFilename = "lcms_positives"
    val lcmsFile = new File(workingDir, lcmsFilename)

    var maxMass = Integer.MAX_VALUE
    if (cl.hasOption(OPTION_MASS_THRESHOLD)) {
      maxMass = Integer.parseInt(cl.getOptionValue(OPTION_MASS_THRESHOLD))
    }

    val positiveRate = cl.getOptionValue(OPTION_LCMS_POSITIVE_RATE).toDouble;

    val massFilteringJob = JavaJobWrapper.wrapJavaFunction(L2InchiCorpus.getRunnableSubstrateFilterer(
      rawSubstratesFile,
      filteredSubstratesFile,
      maxMass));
    headerJob.thenRun(massFilteringJob)

    // Build one job per RO for L2 expansion
    val singleThreadExpansionJobs =
      roIds.map(roId =>
        JavaJobWrapper.wrapJavaFunction(
          L2ExpansionDriver.getRunnableOneSubstrateRoExpander(
            List(roId).asJava,
            filteredSubstratesFile,
            predictionsFiles(roId))))
    // Run one job per RO for L2 expansion
    headerJob.thenRunBatch(singleThreadExpansionJobs)

    // TODO: when Michael adds the capability, change this workflow to run the clustering jobs and LCMS job in parallel
    // Build one job per RO for clustering
    val clusteringJobs =
      roIds.map(roId =>
        JavaJobWrapper.wrapJavaFunction(LibMcsClustering.getRunnableClusterer(
          predictionsFiles(roId),
          sarTreeFiles(roId))))
    // Run one job per RO for clustering
    headerJob.thenRunBatch(clusteringJobs)

    // TODO: when Vijay's LCMS code is ready, replace this with the real thing
    // Build a dummy LCMS job that doesn't do anything.
    val lcmsJob = JavaJobWrapper.wrapJavaFunction(
      new JavaRunnable {
        override def run(): Unit = {
          print("Running dummy LCMS job: didn't implement LCMS workflow job yet.")
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
          sarTreeFiles(roId),
          scoredSarsFiles(roId),
          positiveRate)
      )
    )
    // Run one job per RO for scoring
    headerJob.thenRunBatch(scoringJobs)

    headerJob
  }

  def buildFilesForRos(workingDir: String, fileName: String, roIds: List[Integer]): Map[Integer, File] = {
    Map() ++ roIds.map(r => (r, new File(workingDir, fileName + "." + r.toString)))
  }
}
