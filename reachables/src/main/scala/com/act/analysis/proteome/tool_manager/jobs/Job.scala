package com.act.analysis.proteome.tool_manager.jobs

import org.hsqldb.lib.CountUpDownLatch

import scala.collection.mutable.ListBuffer

abstract class Job {
  protected val jobBuffer = ListBuffer[List[Job]]()
  // How many jobs need to return to this one prior to it starting
  // This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
  protected val returnCounter = new CountUpDownLatch()
  protected var status = JobStatus.Unstarted
  protected var jobReturnCode = -1
  protected var retryJob: Option[Job] = None
  protected var returnJob: Option[Job] = None

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

  def getJobStatus(): String = {
    this.status
  }

  protected def setJobStatus(newStatus: String): Unit = {
    JobManager.logInfo(s"Job status for command ${this} has changed to ${newStatus}")
    status = newStatus
    // Job manager should know if has been marked as complete
    if (this.isCompleted()) {
      JobManager.indicateJobCompleteToManager()
    }
  }

  def thenRunAtPosition(job: Job, position: Int): Job = {
    if (position >= jobBuffer.length) {
      thenRun(job)
    } else {
      jobBuffer(position) = jobBuffer(position) ::: List(job)
    }
    this
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
  def thenRunBatch(nextJobs: List[Job], batchSize: Int = 20): Job = {
    // We use a batch size here to make it so we don't spend all our computation managing job context.
    val jobGroups = nextJobs.grouped(batchSize)
    jobGroups.foreach(x => jobBuffer.append(x))
    this
  }

  def setJobToRunPriorToRetry(job: Job): Job = {
    retryJob = Option(job)
    this
  }

  def start(): Unit = {
    JobManager.logInfo(s"Started command ${this}")
    setJobStatus(JobStatus.Running)
    asyncJob()
  }

  def asyncJob()

  protected def markAsSuccess(): Unit = {
    // The success is if the future succeeded.
    // We need to also check the return code and redirect to failure here if it completed, but with a bad return code
    setJobStatus(JobStatus.Success)

    if (returnJob.isDefined) {
      // Decrease return number
      returnJob.get.decreaseReturnCount()

      // Try to start it again and let it handle if it should
      returnJob.get.runNextJob()
    }

    runNextJob()
  }

  protected def markJobsAfterThisAsFailure(): Unit = {
    jobBuffer.map(jobTier => jobTier.map(jobAtTier => jobAtTier.setJobStatus(JobStatus.ParentProcessFailure)))
  }

  protected def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    if (retryJob.isDefined) {
      JobManager.logInfo(s"Running retry job ${retryJob.get}. ${this} has encountered an error")
      runRetryJob()
    } else {
      setJobStatus(JobStatus.Failure)

      // Mark any jobs still in the buffer as ParentProcessFailure
      if (returnJob.isDefined) returnJob.get.markJobsAfterThisAsFailure()

      // Map this job's buffer as a failure
      this.markJobsAfterThisAsFailure()
    }
  }

  protected def setReturnCode(returnCode: Int): Unit = {
    JobManager.logInfo(s"Command ${this} has changed return code to ${returnCode}")
    jobReturnCode = returnCode
  }

  /*
    Local job continuation utilities
  */
  protected def runRetryJob(): Unit = {
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

  protected def setReturnJob(job: Job): Unit = {
    returnJob = Option(job)
  }

  protected def decreaseReturnCount(): Unit = {
    returnCounter.countDown()
  }

  protected def runNextJob(): Unit = {
    if (returnCounter.getCount > 0) {
      return
    }

    // Start next batch if exists
    if (jobBuffer.nonEmpty) {
      val head = jobBuffer.head
      jobBuffer -= head

      // The number of jobs that need to return to this job prior to it being able to keep going
      returnCounter.setCount(head.length)

      // Map head jobs in
      head.map(x => {
        // When complete, setup return to this job so we can continue decreasing this buffer
        x.setReturnJob(this)
        x.start()
      })
    }
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