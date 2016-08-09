package com.act.biointerpretation

import java.io.File

import com.act.biointerpretation.l2expansion.L2ExpansionDriver
import com.act.biointerpretation.sarinference.LibMcsClustering
import com.act.jobs.JavaRunnable
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}

class UntargetedMetabolomicsWorkflow extends Workflow with WorkingDirectoryUtility {

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_SUBSTRATES = "s"
  private val OPTION_RO_IDS = "r"
  private val OPTION_MASS_THRESHOLD = "m"

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

      CliOption.builder()
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

    if (!verifyInputFilePath(cl.getOptionValue(OPTION_SUBSTRATES))) {
      throw new RuntimeException("Substrates input file not valid.")
    }
    if (!verifyInputFilePath(cl.getOptionValue(OPTION_RO_IDS))) {
      throw new RuntimeException("RO list input file not valid.")
    }

    val substratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATES))
    val roIdFile = new File(cl.getOptionValue(OPTION_RO_IDS))

    val predictionsFilename = "predictions"
    val predictionsFile = new File(workingDir, predictionsFilename)

    val sarTreeFilename = "sartree"
    val sarTreeFile = new File(workingDir, sarTreeFilename)

    val scoredSarsFilename = "scored_sars"
    val scoredSarsFile = new File(workingDir, scoredSarsFilename)

    val lcmsFilename = "lcms_positives"
    val lcmsFile = new File(workingDir, lcmsFilename)

    var maxMass = Integer.MAX_VALUE
    if (cl.hasOption(OPTION_MASS_THRESHOLD)) {
      maxMass = Integer.parseInt(cl.getOptionValue(OPTION_MASS_THRESHOLD))
    }

    val singleThreadExpansionJob = JavaJobWrapper.wrapJavaFunction(
      L2ExpansionDriver.getRunnableOneSubstrateRoExpander(roIdFile, substratesFile, predictionsFile, maxMass))
    headerJob.thenRunAtPosition(singleThreadExpansionJob, 0)

    val clusteringJob = JavaJobWrapper.wrapJavaFunction(
      LibMcsClustering.getRunnableClusterer(predictionsFile, sarTreeFile))
    headerJob.thenRunAtPosition(clusteringJob, 1)

    // TODO: implement this and put in the real thing
    val lcmsJob = JavaJobWrapper.wrapJavaFunction(
      new JavaRunnable {
        override def run(): Unit = ???
      }
    )
    headerJob.thenRunAtPosition(lcmsJob, 1)

    val scorer = JavaJobWrapper.wrapJavaFunction(
      LibMcsClustering.getRunnableSarScorer(sarTreeFile, lcmsFile, scoredSarsFile))
    headerJob.thenRunAtPosition(scorer, 1)

    headerJob
  }
}
