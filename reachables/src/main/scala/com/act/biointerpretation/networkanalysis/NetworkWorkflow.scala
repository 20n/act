package com.act.biointerpretation.networkanalysis

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._

class NetworkWorkflow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to run basic build of a network from input corpuses."

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_INPUT_DIRECTORIES = "i"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("The directory in which to run and create all intermediate files. This directory will be created if it " +
          "does not already exist.").
        required(),

      CliOption.builder(OPTION_INPUT_DIRECTORIES).
        hasArgs.valueSeparator(',').
        desc("The directories in which to find the input corpuses.").
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

    /**
      * Handle command line args and create files
      */
    val workingDirPath = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val workingDir: File = new File(workingDirPath)

    val inputDirs = cl.getOptionValues(OPTION_INPUT_DIRECTORIES).map(path => new File(path))

    def findInputFiles(directory: File): List[File] = {
      val inputFiles = directory.listFiles().toList
      inputFiles.filter(f => !f.isDirectory)
      inputFiles.foreach(f => verifyInputFile(f))
      inputFiles
    }

    val inputFiles = inputDirs.flatMap(inputDir => findInputFiles(inputDir))

    val outputFile = new File(workingDir, "networkOutput")
    verifyOutputFile(outputFile)

    val networkBuilder = new NetworkBuilder(inputFiles.toList.asJava, outputFile)
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network builder", networkBuilder))

    val networkStats = new NetworkStats(outputFile);
    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("network stats", networkStats))
    headerJob
  }
}
