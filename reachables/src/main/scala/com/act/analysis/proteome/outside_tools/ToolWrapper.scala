package com.act.analysis.proteome.outside_tools

import java.io.{File, FileNotFoundException}

/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper

  private var binaries = ""

  protected def constructJob(toolFunction: String, args: List[String], retryJob: Boolean = false): Job = {
    val command = constructCommand(toolFunction, args)

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
