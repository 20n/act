package com.act.analysis.proteome.tool_manager.tool_wrappers

import java.io.File

import com.act.analysis.proteome.tool_manager.jobs.ShellJob
import com.act.analysis.proteome.tool_manager.jobs.management.JobManager
/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper

  private var binaries = ""
  protected def constructJob(toolFunction: String, args: List[String], retryJob: Boolean = false): ShellJob = {
    val usingTool = !toolFunction.equals("") | binaryLocationSet

    if (usingTool) {
      val command = constructCommand(toolFunction, args)
      helperConstructJob(command, retryJob)
    } else {
      helperConstructJob(args, retryJob)
    }
  }

  private def helperConstructJob(command: List[String], retryJob: Boolean = false): ShellJob = {
    // Retry jobs shouldn't be tracked.  We'll let the initial job handle adding the retry job in
    val job = new ShellJob(command)
    if (!retryJob)
      JobManager.addJob(job)

    // Auto log
    job.writeOutputStreamToLogger()
    job.writeErrorStreamToLogger()
    job
  }

  /**
    * Takes a tool command and maps to an absolute path with correct args.
    *
    * @param toolFunction - The name of the binary file to be called
    * @param args         - Any args that come after the binary name, unpacks with spaces between each list element
    *
    * @return Constructed command ready to run
    */
  private def constructCommand(toolFunction: String, args: List[String]): List[String] = {
    val binaryAndToolStringsSet = !toolFunction.equals("") && binaryLocationSet

    if (binaryAndToolStringsSet) {
      val binariesFile = new File(getBinariesLocation, toolFunction)
      List[String](binariesFile.getAbsolutePath) ::: args
    } else {
      (List[String](getBinariesLocation, toolFunction) ::: args).filter(x => !x.equals(""))
    }
  }

  private def binaryLocationSet: Boolean = {
    !getBinariesLocation.equals("")
  }

  private def getBinariesLocation: String = {
    binaries
  }

  def setBinariesLocation(binaryDirectory: String): Unit = {
    binaries = binaryDirectory
  }
}
