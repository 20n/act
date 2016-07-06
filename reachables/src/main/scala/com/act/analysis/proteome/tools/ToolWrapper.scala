package com.act.analysis.proteome.tools

import java.io.{File, PrintWriter}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Wrapper class for tools that allows for tracking of future jobs
  * and makes a few utility functions consistent throughout
  */
abstract class ToolWrapper {
  // Tracks all jobs running as futures within the ToolWrapper
  // Can be used to ensure completion of all jobs w/ awaitUntilAllJobsComplete()
  private val futures = new ListBuffer[Future[Any]]()
  private var binaries = ""

  /**
    * Utility method that maps a function class to a futures call, meaning we can run multiple
    * Tool jobs without worrying about blocking up our program.  Effectively daemonizes the function calls.
    *
    * @param f - Function that we will run as a future
    * @return
    */
  def nonblockingJobWrapper(f: => Any): Any = {
    val future = Future {
      blocking {
        println("Job Started")
        try {
          f
        } catch {
          case e: Exception => println(e.getMessage)
        }

        println("Job Completed")
      }
    }
    futures.append(future)

    // Remove future from futures queue
    future.onComplete({
      case Success(x) => this.futures -= future
      case Failure(x) => this.futures -= future
    })

  }

  /**
    * Takes a tool command and maps to an absolute path with correct args.
    *
    * @param toolFunction - The name of the binary file to be called
    * @param args         - Any args that come after the binary name, unpacks with spaces between each list element
    * @return Constructed command ready to run
    */
  def constructCommand(toolFunction: String, args: List[String]): String = {
    require(requirement = getBinariesLocation() != "",
      message = "Please set binary location of tool prior to running commands: " +
        " \"setBinariesLocation(<Location as a string>)\".")

    // Call the appropriate binary for a tool based on the name and binary location
    // Also unpack the args and place a space between each
    s"${getBinariesLocation()}$toolFunction ${args.mkString(sep = " ")}"
  }

  def getBinariesLocation(): String = {
    binaries
  }

  def setBinariesLocation(binaryDirectory: String): Unit = {
    binaries = binaryDirectory
  }

  /**
    * Utility function which takes a file path, creates everything up to it, then writes the information into the file.
    *
    * @param filePath    Where to save the information
    * @param information A string to be saved within a file
    */
  def saveToOutputFile(filePath: String, information: String): Unit = {
    val file = new File(filePath)
    file.getParentFile.mkdirs()

    val writer = new PrintWriter(file)
    writer.write(information)
    writer.close()
  }

  /**
    * To use futures, we need to keep our program alive by sleeping the thread and checking for it to complete.
    * The callbacks from the nonblocking job wrapper will add and remove from the list until hopefully no more jobs exist.
    *
    * Should be called at the end of a job instantiation set so that any long-running jobs will finish.
    *
    * Default duration is checking every 30 seconds, sleepDuration option allows this to be changed.
    */
  def awaitUntilJobsComplete(sleepDuration: Duration = 30 seconds): Unit = {
    while (!allJobsComplete()) {
      // Most of the syntax here is just pluralization.
      println(s"There ${if (futures.length == 1) "is" else "are"} " +
        s"still ${futures.length} job${if (futures.length == 1) "" else "s"} running")

      // Thread.sleep takes millis, so need to convert
      Thread.sleep(sleepDuration.toMillis)
    }
    println("All jobs complete")
  }

  /**
    * If there are no jobs in the futures queue we are all good
    *
    * @return
    */
  def allJobsComplete(): Boolean = {
    futures.isEmpty
  }
}
