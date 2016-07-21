package com.act.analysis.proteome.tool_manager.jobs

import java.io.{File, PrintWriter}

import org.apache.logging.log4j.LogManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.sys.process.{BasicIO, Process, ProcessIO, ProcessLogger, _}
import scala.util.{Failure, Success}

class ShellJob(commands: List[String]) extends Job {
  private val logger = LogManager.getLogger(getClass.getName)

  private var outputMethod: Option[(String) => Unit] = None
  private var errorMethod: Option[(String) => Unit] = None

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
      case Success(x) => markJobSuccessBasedOnReturnCode(x.exitValue())
      // This is a failure of the future to complete because of a JVM exception
      case Failure(x) => markAsFailure()
    })
  }

  // Setup output process
  private def setupProcessIO(): ProcessIO = {
    val jobIO = ProcessLogger(
      (output: String) => if (outputMethod.isDefined) outputMethod.get(s"[stdout] $output"),
      (error: String) => if (errorMethod.isDefined) errorMethod.get(s"[stderr] $error")
    )

    BasicIO.apply(withIn = false, jobIO)
  }

  protected def markJobSuccessBasedOnReturnCode(returnCode: Int): Unit = {
    setReturnCode(returnCode)
    if (returnCode != 0) markAsFailure() else markAsSuccess()
  }

  def writeOutputStreamToFile(file: File): Job = {
    outputMethod = Option(writeStreamToFile(file))
    this
  }

  // Internal handling out streams
  // To File
  private def writeStreamToFile(file: File)(output: String): Unit = {
    val directory = file.getParent
    new File(directory).mkdirs

    val writer = new PrintWriter(file)
    writer.write(output)
    writer.close()
  }

  def writeErrorStreamToFile(file: File): Job = {
    errorMethod = Option(writeStreamToFile(file))
    this
  }

  // job1.writeOutputStreamToLogger.thenRun(job2)
  def writeOutputStreamToLogger(): Job = {
    outputMethod = Option(writeStreamToLogger(logger.info))
    this
  }

  // To Logger
  private def writeStreamToLogger(loggerType: (String) => Unit)(output: String): Unit = {
    loggerType(output)
  }

  def writeErrorStreamToLogger(): Job = {
    errorMethod = Option(writeStreamToLogger(logger.error))
    this
  }

  override def toString: String = {
    commands.mkString(sep = " ")
  }
}
