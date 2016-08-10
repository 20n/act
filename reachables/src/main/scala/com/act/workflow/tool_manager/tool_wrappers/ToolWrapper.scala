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

  protected def constructJob(toolFunction: Option[String], args: List[String], retryJob: Boolean = false): ShellJob = {
    val usingTool = toolFunction.isDefined | binaries.isDefined

    if (usingTool) {
      val command = constructToolCommand(toolFunction, args)
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
  private def constructToolCommand(toolFunction: Option[String], args: List[String]): List[String] = {
    binaries match {


      // Binaries exist
      case binary if binary.isDefined =>
        toolFunction match {

          // Tool doesn't exist
          case tool if tool.isDefined =>
            val binariesFile = new File(binaries.get.getAbsolutePath, toolFunction.get)
            List[String](binariesFile.getAbsolutePath) ::: args

          // Tool does exist
          case toolExists =>
            List[String](binaries.get.getAbsolutePath) ::: args
        }


      // Binaries don't exist
      case binaryDoesNotExist =>
        toolFunction match {

          // Tool doesn't exist
          case tool if tool.isDefined =>
            List[String](toolFunction.get) ::: args

          // Tool does exist
          case toolDoesNotExist =>
            throw new RuntimeException("Neither binaries nor tools set, but constructing tool command.")
        }
    }
  }
}
