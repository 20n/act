package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.{AtomicLatch, JobManager}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

abstract class Job(name: String) {
  protected val jobBuffer = ListBuffer[List[Job]]()
  /*
   How many jobs need to return to this one prior to it starting
   This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
   */
  protected val returnCounter = new AtomicLatch
  private val logger = LogManager.getLogger(getClass.getName)
  protected var jobReturnCode = -1
  protected var retryJob: Option[Job] = None
  protected var returnJob: Option[Job] = None
  protected var cancelFuture: Option[() => Boolean] = None
  private var status = JobStatus.Unstarted

  /*
  A bunch of basic getters that determine the logic of the job
   */
  def returnCode(): Int = {
    jobReturnCode
  }

  def isCompleted: Boolean = synchronized {
    isSuccessful | isFailed | isKilled
  }

  def isKilled: Boolean = synchronized {
    getJobStatus == JobStatus.Killed
  }

  def isSuccessful: Boolean = synchronized {
    getJobStatus == JobStatus.Success
  }

  def isFailed: Boolean = synchronized {
    val currentJobStatus = getJobStatus
    currentJobStatus == JobStatus.Failure |
      currentJobStatus == JobStatus.ParentProcessFailure |
      currentJobStatus == JobStatus.Killed
  }

  def isRunning: Boolean = synchronized {
    getJobStatus == JobStatus.Running
  }

  def isUnstarted: Boolean = synchronized {
    getJobStatus == JobStatus.Unstarted
  }

  def getJobStatus: String = synchronized {
    this.status
  }

  /**
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
    */
  protected def setJobStatus(newStatus: String): Unit = {
    /*
        We need to synchronize this so that status updates don't start hitting race conditions.
        We only synchronize over the status update because otherwise
        we have locking problems because isCompleted is also synchronized.
      */
    this.synchronized {
      logger.info(s"Job status for command ${this} has changed to $newStatus")
      status = newStatus
    }

    // Job manager should know if has been marked as complete
    if (isCompleted) JobManager.indicateJobCompleteToManager(this)
  }

  /*
    User description of job

    Any method here should return this job to allow for chaining
    Allows sequential job chaining (Ex: job1.thenRun(job2).thenRun(job3))
   */
  /**
    * Add a job to the next location to sequentially execute jobs
    *
    * Example: job1.thenRun(job2).thenRun(job3)
    *
    * Should run job1 -> job2 -> job3 in order.
    *
    * @param nextJob Job to run after the current last one
    *
    * @return this, for chaining
    */
  def thenRun(nextJob: Job): Job = {
    jobBuffer.append(List(nextJob))
    this
  }

  /** Run chained jobs in parallel
    * job1.thenRunBatch(List(job2, job3, job4)).thenRun(job5)
    *
    * Should run (Hashtags are important for picture framing)
    * #          ------> job2 ---
    * #       /                    \
    * #     job1 ---> job3 ---> job 5
    * #       \                   /
    * #          ------> job4 ---
    *
    * @param nextJobs  A list of jobs that should be run after the current last one
    * @param batchSize If too many jobs are added and asked to be run concurrently, concurrency operations are not
    *                  handled well.  Thus, we limit, by default, the number of jobs that can be indicated to run
    *                  concurrently at 20.  If more than 20 jobs are supplied,
    *                  we break into groups of 20 and run sequentially.
    *
    * @return this, for chaining
    */
  def thenRunBatch(nextJobs: List[Job], batchSize: Int = 20): Job = {
    // We use a batch size here to make it so we don't spend all our computation managing job context.
    val jobGroups = nextJobs.grouped(batchSize)
    jobGroups.foreach(x => jobBuffer.append(x))
    this
  }

  /**
    * This job will be run if the current job fails, prior to attempt to rerun the current job.
    *
    * @param job job to run prior to retrying
    *
    * @return this, for chaining
    */
  def setJobToRunPriorToRetry(job: Job): Job = {
    retryJob = Option(job)
    this
  }

  /**
    * Run the async job and sets status to 'Running'
    */
  def start(): Unit = {
    if (JobStatus.Killed != getJobStatus) {
      logger.info(s"Started command ${this}")
      setJobStatus(JobStatus.Running)
      asyncJob()
    }
  }

  /**
    * Defined by the given type of job to effectively run asynchronously.
    */
  def asyncJob()

  override def toString: String = {
    s"[Job]<${this.name}>"
  }

  /**
    * Kills a job if it is currently not started.
    */
  def killUncompleteJob {
    // Only kill jobs if they haven't completed already.
    if (!this.isCompleted) {
      // Currently running needs to cancel future.
      if (this.cancelFuture.isDefined) {
        this.cancelFuture.get()
      }

      setJobStatus(JobStatus.Killed)
      jobBuffer.foreach(jobTier => jobTier.foreach(
        jobAtTier => {
          jobAtTier.setJobStatus(JobStatus.Killed)
          jobAtTier.killUncompleteJob
        }))
    }
  }

  def getName: String = {
    this.name
  }

  /**
    * Usually called from an asyncJob when it has completed.
    *
    * If it was called from another job:
    * Updates the number of jobs that still need to complete,
    * then notifies the job it was called from that it should check if it should run the next jobs.
    *
    * Runs the jobs after it, if any.
    *
    */
  protected def markAsSuccess(): Unit = {
    /*
      The success is if the future succeeded.
      We need to also check the return code and redirect to failure here if it completed, but with a bad return code
     */
    setJobStatus(JobStatus.Success)
    handleIfJobTotallyComplete()
    runNextJobIfReady()
  }

  /**
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  protected def markJobsAfterThisAsFailure(): Unit = {
    jobBuffer.foreach(jobTier => jobTier.foreach(
      jobAtTier => {
        jobAtTier.setJobStatus(JobStatus.ParentProcessFailure)
        jobAtTier.markJobsAfterThisAsFailure()
      })
    )
  }

  /*
    Local job continuation utilities
  */

  /**
    * First checks if there is a retryJob available, in which case it attempts to run it.
    * Otherwise, simply marks this and all jobs after it as a failure.
    */
  protected def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    if (retryJob.isDefined) {
      logger.info(s"Running retry job ${retryJob.get}. ${this} has encountered an error")
      runRetryJob()
    } else {
      setJobStatus(JobStatus.Failure)

      // Mark any jobs still in the buffer as ParentProcessFailure
      if (returnJob.isDefined) returnJob.get.markJobsAfterThisAsFailure()

      // Map this job's buffer as a failure
      this.markJobsAfterThisAsFailure()
    }
  }

  /**
    * Modifies the return code of this element
    *
    * @param returnCode Value given to the return code
    */
  protected def setReturnCode(returnCode: Int): Unit = {
    logger.info(s"Command ${this} has changed return code to $returnCode")
    jobReturnCode = returnCode
  }

  /**
    * Adds all of the retry job's buffer and the retry job itself to the job manager,
    * then sets the current job that failed to run after that.
    *
    * Has a dedicated status, where status = 'Retry'
    *
    * Retry should only happen once, so we wipe it clean afterwards.
    */
  protected def runRetryJob(): Unit = {
    val someJob = retryJob.get

    // Add this job to the job manager
    JobManager.addJob(someJob)
    // Add the rest of the retry job buffer into the JobManager as they shouldn't be added if a retry doesn't happen
    someJob.jobBuffer.foreach(jobTier => jobTier.foreach(jobAtTier => JobManager.addJob(jobAtTier)))

    // Run the current job after this retry job goes through
    someJob.thenRun(this)
    setJobStatus(JobStatus.Retry)

    // Remove retry job so it only retries once
    retryJob = None

    someJob.start()
  }

  /**
    * Modify which job this job should call back to after finishing
    *
    * @param job Job to call back to after finishing
    */
  protected def setReturnJob(job: Job): Unit = {
    returnJob = Option(job)
  }

  /**
    * Decreases the lock counter
    */
  protected def decreaseReturnCount(): Unit = {
    returnCounter.countDown()
    handleIfJobTotallyComplete()
  }

  protected def handleIfJobTotallyComplete(): Unit = {
    if (returnJob.isDefined && returnCounter.getCount <= 0 && jobBuffer.length <= 0) {
      // Decrease return number
      returnJob.get.decreaseReturnCount()

      // Try to start it again and let it handle if it should
      returnJob.get.runNextJobIfReady()
    }
  }

  /**
    * Checks to make sure we aren't waiting on anymore jobs.
    * If we aren't, removes the first element of the jobBuffer and runs all jobs in that list.
    */
  protected def runNextJobIfReady(): Unit = {
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
      head.foreach(jobInHead => {
        // When complete, setup return to this job so we can continue decreasing this buffer
        jobInHead.setReturnJob(this)
        jobInHead.start()
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
    val Killed = "Killed"
  }
}
