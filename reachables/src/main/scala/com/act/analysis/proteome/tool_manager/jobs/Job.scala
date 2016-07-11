package com.act.analysis.proteome.tool_manager.jobs

import java.io.{File, PrintWriter}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.sys.process._
import scala.util.{Failure, Success}

class Job(commands: List[String]) {
  private val jobBuffer = ListBuffer[List[Job]]()
  private val out = new StringBuilder("Output Stream:\n")
  private val err = new StringBuilder("Error Stream:\n")
  private var status = JobStatus.Unstarted
  private var jobReturnCode = -1
  private var retryJob: Option[Job] = None
  private var outputStreamHandling: Option[(StringBuilder) => Unit] = None
  private var errorStreamHandling: Option[(StringBuilder) => Unit] = None

  // How many jobs need to return to this one prior to it starting
  // This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
  private var returnCount: Option[Int] = None
  private var returnJob: Option[Job] = None

  def returnCode(): Int = {
    jobReturnCode
  }

  def isCompleted(): Boolean = {
    isSuccessful | isFailed
  }

  def isSuccessful(): Boolean = {
    this.status == JobStatus.Success
  }

  def isFailed(): Boolean = {
    this.status == JobStatus.Failure | this.status == JobStatus.ParentProcessFailure
  }

  def isRunning(): Boolean = {
    this.status == JobStatus.Running
  }

  def isUnstarted(): Boolean = {
    this.status == JobStatus.Unstarted
  }

  /*
User description of job

Any public method here should return this job to allow for chaining
 */
  // Allows sequential job chaining
  // Ex: job1.thenRun(job2).thenRun(job3)
  def thenRun(nextJob: Job): Job = {
    jobBuffer.append(List(nextJob))
    this
  }

  // Run chained jobs in parallel
  // job1.thenRunBatch(List(job2, job3, job4))
  def thenRunBatch(nextJobs: List[Job]): Job = {
    jobBuffer.append(nextJobs)
    this
  }

  //
  def setJobToRunPriorToRetry(job: Job): Job = {
    retryJob = Option(job)
    this
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

  /*
Launch jobs
 */
  def start(): Unit = {
    JobManager.logInfo(s"Started command ${this}")
    setJobStatus(JobStatus.Running)
    asyncJob()
  }

  /*
  Describe job
   */
  @Override
  override def toString(): String = {
    commands.mkString(sep = " ")
  }

  private def setJobStatus(newStatus: String): Unit = {
    JobManager.logInfo(s"Job status for command ${this} has changed to ${newStatus}")
    status = newStatus

    // Job manager should know if has been marked as complete
    if (this.isCompleted()) JobManager.indicateJobCompleteToManager()
  }

  private def markJobSuccessBasedOnReturnCode(returnCode: Int): Unit = {
    setReturnCode(returnCode)
    if (returnCode != 0) markAsFailure()
    else markAsSuccess()
  }

  private def markAsSuccess(): Unit = {
    // The success is if the future succeeded.
    // We need to also check the return code and redirect to failure here if it completed, but with a bad return code
    handleStreams()
    setJobStatus(JobStatus.Success)
    if (returnJob.isDefined) {
      // Decrease return number
      returnJob.get.decreaseReturnCount()

      // Try to start it again and let it handle if it should
      returnJob.get.runNextJob()
    }
    runNextJob()
  }

  private def markJobsAfterThisAsFailure(): Unit = {
    jobBuffer.map(jobTier => jobTier.map(jobAtTier => jobAtTier.setJobStatus(JobStatus.ParentProcessFailure)))
  }

  private def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    if (retryJob.isDefined) {
      JobManager.logInfo(s"Running retry job ${retryJob.get}. ${this} has encountered an error")
      runRetryJob()
    } else {
      handleStreams()
      setJobStatus(JobStatus.Failure)

      // Mark any jobs still in the buffer as ParentProcessFailure
      if (returnJob.isDefined) returnJob.get.markJobsAfterThisAsFailure()
    }
  }

  private def setReturnCode(returnCode: Int): Unit = {
    JobManager.logInfo(s"Command ${this} has changed return code to ${returnCode}")
    jobReturnCode = returnCode
  }

  /*
    Local job continuation utilities
  */
  private def runRetryJob(): Unit = {
    val someJob = retryJob.get

    // Add this job to the job manager
    JobManager.addJob(someJob)
    // Add the rest of the retry job buffer into the JobManager as they shouldn't be added if a retry doesn't happen
    someJob.jobBuffer.map(jobTier => jobTier.map(jobAtTier => JobManager.addJob(jobAtTier)))

    // Run the current job after this retry job goes through
    someJob.thenRun(this)
    setJobStatus(JobStatus.Retry)

    retryJob.get.start()

    // Remove retry job so it only retries once
    retryJob = None
  }

  private def setReturnJob(job: Job): Unit = {
    returnJob = Option(job)
  }

  private def decreaseReturnCount(): Unit = {
    returnCount = Option(returnCount.get - 1)
  }

  private def runNextJob(): Unit = {
    if (returnCount.isDefined) {
      // We still need to return from previous jobs so don't start this one just yet
      if (returnCount.get > 0) return
    }

    // Start next batch if exists
    if (jobBuffer.nonEmpty) {
      val head = jobBuffer.head
      jobBuffer -= head

      // The number of jobs that need to return to this job prior to it being able to keep going
      returnCount = Option(head.length)

      // Map head jobs in
      head.map(x => {
        // When complete, setup return to this job so we can continue decreasing this buffer
        x.setReturnJob(this)
        x.start()
      })
    }
  }

  private def handleStreams(): Unit = {
    // If an output stream function has been set, pass the streams to it so that it can handle it
    if (outputStreamHandling.isDefined) outputStreamHandling.get(out)
    if (errorStreamHandling.isDefined) errorStreamHandling.get(err)
  }

  private def setupProcessIO(): ProcessIO = {
    val jobIO = ProcessLogger(
      (output: String) => out.append(output + "\n"),
      (error: String) => err.append(error + "\n")
    )

    BasicIO.apply(withIn = false, jobIO)
  }

  private def asyncJob(): Any = {
    // Run the call in the future
    val future: Future[Process] = Future {
      commands.run(setupProcessIO())
    }

    // Setup Job's success/failure
    future.onComplete({
      // Does not mean that the job succeeded, just that the future did
      case Success(x) => markJobSuccessBasedOnReturnCode(x.exitValue())
      // This is a failure of the future to complete because of a JVM exception
      case Failure(x) => markAsFailure()
    })
  }


  /*
    Update and query job status
  */
  object JobStatus extends Enumeration {
    type Status = Value
    val Success = "Success"
    val Retry = "Retrying"
    val Failure = "Failure"
    val Running = "Running"
    val Unstarted = "Unstarted"
    val ParentProcessFailure = "Parent Process Failed"
  }

}