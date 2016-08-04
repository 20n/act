package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.{AtomicLatch, JobManager}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

abstract class Job {
  protected val jobBuffer = ListBuffer[List[Job]]()
  /*
   How many jobs need to return to this one prior to it starting
   This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
   */
  protected val returnCounter = new AtomicLatch
  private val logger = LogManager.getLogger(getClass.getName)
  protected var status = JobStatus.Unstarted
  protected var jobReturnCode = -1
  protected var retryJob: Option[Job] = None
  protected var returnJob: Option[Job] = None

  /*
  A bunch of basic getters that determine the logic of the job
   */
  def returnCode(): Int = {
    jobReturnCode
  }

  def isCompleted: Boolean = {
    isSuccessful | isFailed
  }

  def isSuccessful: Boolean = {
    this.status == JobStatus.Success
  }

  def isFailed: Boolean = {
    this.status == JobStatus.Failure | this.status == JobStatus.ParentProcessFailure
  }

  def isRunning: Boolean = {
    this.status == JobStatus.Running
  }

  def isUnstarted: Boolean = {
    this.status == JobStatus.Unstarted
  }

  def getJobStatus: String = {
    this.status
  }

  /**
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
    */
  protected def setJobStatus(newStatus: String): Unit = {
    logger.info(s"Job status for command ${this} has changed to $newStatus")
    status = newStatus

    // Job manager should know if has been marked as complete
    if (isCompleted) JobManager.indicateJobCompleteToManager()
  }

  /*
    User description of job

    Any method here should return this job to allow for chaining
    Allows sequential job chaining (Ex: job1.thenRun(job2).thenRun(job3))
   */

  /**
    * Allows the user to indicate which spot in the job queue a job should be placed at.
    * If the job queue is not currently that long,
    * it will indicate that the job has been placed at the next nearest opportunity.
    *
    * The utility of this function is that it allows for iterative procedures to place desired jobs at similar places.
    * For example, if we are iterating over a list of 10 of the same jobs,
    * we can either collect them all and then run as a batch,
    * or just know where we want to put them and just indicate that in the iterative procedure.
    *
    * @param job      Job to be added to the list at the position.
    * @param position Which position in the list of lists to append this job to.
    *                 If position is >= buffer length, should be appended to the end of the jobs list.
    *
    * @return this, for chaining
    */
  def thenRunAtPosition(job: Job, position: Int): Job = {
    if (position >= jobBuffer.length) {
      while (position >= jobBuffer.length) {
        jobBuffer.append(List())
      }
      thenRun(job)
    } else {
      jobBuffer(position) = jobBuffer(position) ::: List(job)
    }
    this
  }

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
    * -> job2 -
    * /           \
    * Should run job1 ---> job3 ---> job 5
    * \           /
    * -> job4 -
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
    logger.info(s"Started command ${this}")
    setJobStatus(JobStatus.Running)
    asyncJob()
  }

  /**
    * Defined by the given type of job to effectively run asynchronously.
    */
  def asyncJob()

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

  /**
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  protected def markJobsAfterThisAsFailure(): Unit = {
    jobBuffer.foreach(jobTier => jobTier.foreach(jobAtTier => jobAtTier.setJobStatus(JobStatus.ParentProcessFailure)))
  }

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

  /*
    Local job continuation utilities
  */
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
  }

  /**
    * Checks to make sure we aren't waiting on anymore jobs.
    * If we aren't, removes the first element of the jobBuffer and runs all jobs in that list.
    */
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
  }

}