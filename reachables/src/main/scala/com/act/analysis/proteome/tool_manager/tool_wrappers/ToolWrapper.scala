package com.act.analysis.proteome.tool_manager.tool_wrappers

import java.io.{File, FileNotFoundException}

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager, ShellJob}
import com.ibm.db2.jcc.t4.ob

/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper

  private var binaries = ""

  protected def constructJob(toolFunction: String, args: List[String], retryJob: Boolean = false): ShellJob = {
    val usingTool = !toolFunction.equals("") | !getBinariesLocation().equals("")

    if (usingTool) {
      val command = constructCommand(toolFunction, args)
      _constructJob(command, retryJob)
    } else {
      _constructJob(args, retryJob)
    }
  }

  private def _constructJob(command: List[String], retryJob: Boolean = false): ShellJob = {
    // Retry jobs shouldn't be tracked.  We'll let the initial job handle adding the retry job in
    val job = new ShellJob(command)
    if (!retryJob)
      JobManager.addJob(job)
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
    val binaryAndToolStrings = !toolFunction.equals("") && !getBinariesLocation().equals("")
    if (binaryAndToolStrings){
      val binariesFile = new File(getBinariesLocation(), toolFunction)
      List[String](binariesFile.getAbsolutePath) ::: args
    } else {
      (List[String](getBinariesLocation(), toolFunction) ::: args).filter(x => !x.equals(""))
    }
  }


  private def getBinariesLocation(): String = {
    binaries
  }

  def setBinariesLocation(binaryDirectory: String): Unit = {
    binaries = binaryDirectory
  }
}
