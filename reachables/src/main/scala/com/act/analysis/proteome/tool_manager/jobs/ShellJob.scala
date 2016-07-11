package com.act.analysis.proteome.tool_manager.jobs
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.{File, PrintWriter}

import scala.concurrent.{Future, blocking}
import scala.sys.process.{BasicIO, Process, ProcessIO, ProcessLogger}
import scala.sys.process._
import scala.util.{Failure, Success}

class ShellJob(commands: List[String]) extends Job {
  private val out = new StringBuilder("Output Stream:\n")
  private val err = new StringBuilder("Error Stream:\n")

  private var outputStreamHandling: Option[(StringBuilder) => Unit] = None
  private var errorStreamHandling: Option[(StringBuilder) => Unit] = None

  def asyncJob() {
    // Run the call in the future
    val future: Future[Process] = Future {
      blocking {
        commands.run(setupProcessIO())
      }
    }

    // Setup Job's success/failure
    future.onComplete({
      // Does not mean that the job succeeded, just that the future did
      case Success(x) => {
        markJobSuccessBasedOnReturnCode(x.exitValue())
      }
      // This is a failure of the future to complete because of a JVM exception
      case Failure(x) => markAsFailure()
    })
  }

  private def handleStreams(): Unit = {
    // If an output stream function has been set, pass the streams to it so that it can handle it
    if (outputStreamHandling.isDefined) outputStreamHandling.get(out)
    if (errorStreamHandling.isDefined) errorStreamHandling.get(err)
  }

  // Setup output process
  private def setupProcessIO(): ProcessIO = {
    val jobIO = ProcessLogger(
      (output: String) => out.append(output + "\n"),
      (error: String) => err.append(error + "\n")
    )

    BasicIO.apply(withIn = false, jobIO)
  }

  def writeOutputStreamToFile(file: File): Job = {
    outputStreamHandling = Option(writeStreamToFile(file))
    this
  }

  // Internal handling out streams
  // To File
  private def writeStreamToFile(file: File)(stream: StringBuilder): Unit = {
    val directory = file.getParent
    new File(directory).mkdirs

    val writer = new PrintWriter(file)
    writer.write(stream.toString)
    writer.close()
  }

  def writeErrorStreamToFile(file: File): Job = {
    errorStreamHandling = Option(writeStreamToFile(file))
    this
  }

  // job1.writeOutputStreamToLogger.thenRun(job2)
  def writeOutputStreamToLogger(): Job = {
    outputStreamHandling = Option(writeStreamToLogger)
    this
  }

  // To Logger
  private def writeStreamToLogger(stream: StringBuilder): Unit = {
    JobManager.logInfo(stream.toString)
  }

  def writeErrorStreamToLogger(): Job = {
    errorStreamHandling = Option(writeStreamToLogger)
    this
  }

  protected def markJobSuccessBasedOnReturnCode(returnCode: Int): Unit = {
    setReturnCode(returnCode)
    if (returnCode != 0) {
      if (retryJob.isEmpty)
        handleStreams()
      markAsFailure()
    }
    else {
      handleStreams()
      markAsSuccess()
    }
  }

  override def toString(): String = {
    commands.mkString(sep = " ")
  }
}
