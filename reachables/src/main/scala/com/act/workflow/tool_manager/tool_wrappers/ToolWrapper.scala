package com.act.workflow.tool_manager.tool_wrappers

import java.io.File

import com.act.workflow.tool_manager.jobs.ShellJob
import com.act.workflow.tool_manager.jobs.management.JobManager
/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper

  private var binaries: Option[File] = None

  def setBinariesLocation(binaryDirectory: File): Unit = {
    binaries = Option(binaryDirectory)
  }

  /**
    * Correctly assigns the args to helperConstructJob based on if a tool is being used or not.
    * A tool could either be a binary currently existing or a toolFunction being passed in as an arg (Or both)
    *
    * @param toolFunction A string (Or none) that defines a command line tool.
    * @param args         The args for the tool
    * @param retryJob     If this job will only be run if we are retrying another job.
    *
    * @return The newly constructed job
    */
  protected def constructJob(commandName: String, toolFunction: Option[String],
                             args: List[String], retryJob: Boolean = false): ShellJob = {
    val usingTool = toolFunction.isDefined | binaries.isDefined

    if (usingTool) {
      val command = constructToolCommand(toolFunction, args)
      helperConstructJob(commandName, command, retryJob)
    } else {
      helperConstructJob(commandName, args, retryJob)
    }
  }

  /**
    * Constructs a job. If this job is a retry job then we don't let the
    * JobManager know yet (We will if we end up retrying it)
    *
    * @param command  The fully created list of strings that is a full command.
    * @param retryJob If this job will only be run if we are retrying another job.
    *
    * @return A job, we made it!
    */
  private def helperConstructJob(commandName: String, command: List[String], retryJob: Boolean = false): ShellJob = {
    // Retry jobs shouldn't be tracked.  We'll let the initial job handle adding the retry job in
    val job = new ShellJob(commandName, command)
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
  private def constructToolCommand(toolFunction: Option[String], args: List[String]): List[String] = {
    binaries match {


      // Binaries exist
      case binary if binary.isDefined =>
        toolFunction match {

          // Tool does exist
          case tool if tool.isDefined =>
            val binariesFile = new File(binaries.get.getAbsolutePath, toolFunction.get)
            List[String](binariesFile.getAbsolutePath) ::: args

          // Tool doesn't exist
          case toolDoesNotExist =>
            List[String](binaries.get.getAbsolutePath) ::: args
        }


      // Binaries don't exist
      case binaryDoesNotExist =>
        toolFunction match {

          // Tool does exist
          case tool if tool.isDefined =>
            List[String](toolFunction.get) ::: args

          // Tool doesn't exist
          case toolDoesNotExist =>
            throw new RuntimeException("Neither binaries nor tools set, but constructing tool command.")
        }
    }
  }
}
