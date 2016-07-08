package com.act.analysis.proteome.outside_tools

import java.io.{File, FileNotFoundException}

/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper

  private var binaries = ""

  def constructJob(toolFunction: String, args: List[String], retryJob: Boolean = false): Job = {
    // If there is no tool function assume it is not using a tool
    if (!toolFunction.equals("")) {
      val command = constructCommand(toolFunction, args)
      _constructJob(command, retryJob)
    } else {
      _constructJob(args, retryJob)
    }
  }

  private def _constructJob(command: List[String], retryJob: Boolean = false): Job = {
    // Retry jobs shouldn't be tracked.  We'll let the initial job handle adding the retry job in
    if (retryJob) new Job(command)
    else JobManager.addJob(new Job(command))
  }

  /**
    * Takes a tool command and maps to an absolute path with correct args.
    *
    * @param toolFunction - The name of the binary file to be called
    * @param args         - Any args that come after the binary name, unpacks with spaces between each list element
    * @return Constructed command ready to run
    * @throws FileNotFoundException when unable to find binary file
    */
  private def constructCommand(toolFunction: String, args: List[String]): List[String] = {
    require(requirement = getBinariesLocation() != "",
      message = "Please set binary location of tool prior to running commands: " +
        " \"setBinariesLocation(<Location as a string>)\".")

    val binariesFile = new File(getBinariesLocation(), toolFunction)

    if (!binariesFile.exists())
      throw new FileNotFoundException(s"Unable to find tool $toolFunction at ${binariesFile.getAbsolutePath}")

    List[String](binariesFile.getAbsolutePath) ::: args
  }


  private def getBinariesLocation(): String = {
    binaries
  }

  def setBinariesLocation(binaryDirectory: String): Unit = {
    binaries = binaryDirectory
  }
}
