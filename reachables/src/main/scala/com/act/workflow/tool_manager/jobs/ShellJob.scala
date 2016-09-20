package com.act.workflow.tool_manager.jobs

import java.io.{File, PrintWriter}

import com.act.workflow.tool_manager.jobs.management.utility.CanceleableFuture
import org.apache.logging.log4j.LogManager

import scala.concurrent.CancellationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.{BasicIO, Process, ProcessIO, ProcessLogger, _}
import scala.util.{Failure, Success}

class ShellJob(name: String, commands: List[String]) extends Job(name) {
  private val logger = LogManager.getLogger(getClass.getName)

  private var outputMethod: Option[(String) => Unit] = None
  private var errorMethod: Option[(String) => Unit] = None

  def asyncJob() {
    logger.info(s"Running command line job ${commands.mkString(" ")}")

    // Run the call in the future
    val (future, cancel) = CanceleableFuture.create[Process](future => commands.run(setupProcessIO()))
    addCancelFunction(cancel)

    // Setup Job's success/failure
    future.onComplete({
      case Success(x) => markJobSuccessBasedOnReturnCode(x.exitValue())
      case Failure(x) =>
        if (x.isInstanceOf[CancellationException]) {
          logger.error("Future was canceled.")
        } else {
          markAsFailure()
          logger.error(s"Cause of failure was ${x.getMessage}.", x)
        }
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
    internalState.setReturnCode(returnCode)
    logger.trace(s"Command ${this} has changed return code to $returnCode")
    if (returnCode != 0)
      markAsFailure()
    else
      markAsSuccess()
  }

  def writeOutputStreamToFile(file: File): Job = {
    outputMethod = Option(writeStreamToFile(file))
    this
  }

  def writeErrorStreamToFile(file: File): Job = {
    errorMethod = Option(writeStreamToFile(file))
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

  // job1.writeOutputStreamToLogger.thenRun(job2)
  def writeOutputStreamToLogger(): Job = {
    outputMethod = Option(writeStreamToLogger(logger.info))
    this
  }

  def writeErrorStreamToLogger(): Job = {
    errorMethod = Option(writeStreamToLogger(logger.error))
    this
  }

  // To Logger
  private def writeStreamToLogger(loggerType: (String) => Unit)(output: String): Unit = {
    loggerType(output)
  }

  def doNotWriteOutputStream(): Job = {
    outputMethod = None
    this
  }

  def doNotWriteErrorStream(): Job = {
    errorMethod = None
    this
  }
}
