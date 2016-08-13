package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.jobs.management.utility.AtomicLatch
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

abstract class Job(name: String) {
  protected val jobBuffer = ListBuffer[List[Job]]()
  // Handle interaction with other jobs
  protected val returnCounter = new AtomicLatch
  /*
    How many jobs need to return to this one prior to it starting
    This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
   */
  private val logger = LogManager.getLogger(getClass.getName)
  protected var jobReturnCode = -1
  protected var retryJob: Option[Job] = None
  protected var returnJob: Option[Job] = None
  protected var cancelFuture: Option[() => Boolean] = None
  // Current status
  private var status = JobStatus.Unstarted
  /*
    If true, this job will wait for it to complete prior to launching next round of jobs.
    Otherwise, it will launch whenever all the other jobs it is waiting on are done.
   */
  private var shouldBeWaitedOn = true

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

  def getJobStatus: String = synchronized {
    this.status
  }

  /**
    * Returns a list of all flags that this job possesses
    *
    * @return List of flags
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
    if (isCompleted) {
      JobManager.indicateJobCompleteToManager(this)
    }
  }


  def isUnstarted: Boolean = synchronized {
    getJobStatus == JobStatus.Unstarted
  }

  def getJobBuffer: List[List[Job]] = {
    jobBuffer.toList
  }


  /**
    * If this is set, when this job is added to another job it won't report
    * back to that job that it is complete and the other job will continue even if it isn't complete.
    */
  def jobShouldNotBeWaitedFor {
    shouldBeWaitedOn = false
  }

  /**
    * This job will be run if the current job fails, prior to attempt to rerun the current job.
    *
    * The job given to retryJob is usually not run.  However, it will be added to the Job manager
    * and run if this job fails.  Then, after retryJob completes it will attempt to launch this job again.
    *
    * @param job job to run prior to retrying
    *
    * @return this, for chaining
    */
  def thenRun(nextJob: Job): Job = {
    jobBuffer.append(List(nextJob))

    this
  }
}

  /*
    User description of job

    Any method here should return this job to allow for chaining
    Allows sequential job chaining (Ex: job1.thenRun(job2).thenRun(job3))
   */

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
    jobGroups.foreach(internalState.dependencyManager.appendJobBuffer)
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
    internalState.dependencyManager.appendJobBuffer(nextJob)
    this
  }

  override def toString: String = {
    s"[Job]<$getName>"
  }

  protected def markAsSuccess(): Unit = {
    internalState.markAsSuccess()
  }

  protected def markAsFailure(): Unit = {
    internalState.markAsFailure()
  }
}

class InternalState(job: Job) {
  val runManager = new RunManager(job)
  val statusManager = new StatusManager
  val dependencyManager = new DependencyManager
  private val logger: Logger = LogManager.getLogger(getClass.getName)
  private var returnCode: Int = -1
  private var cancelCurrentJobFunction: Option[() => Boolean] = None

  def setStatus(value: String): Unit = statusManager.setJobStatus(value)

  def getReturnCode: Int = returnCode

  def setReturnCode(value: Int): Unit = returnCode = value

  def setCancelCurrentJobFunction(value: Option[() => Boolean]): Unit = cancelCurrentJobFunction = value

  def setRetryJob(value: Job): Unit = runManager.retryJob = Option(value)

  /**
    * Adds an individual job to the job buffer
    *
    * @param job Job to be added
    */
  def start(): Unit = {
    // Killed jobs should never start
    if (!this.isKilled) {

      if (this.isUnstarted) {
        logger.info(s"Started command ${this}")
        setJobStatus(JobStatus.Running)
        asyncJob()
      } else {
        val message = s"Attempted to start a job that has already been started.  " +
          s"Job name is $getName with current status $getJobStatus"
        logger.error(message)
        throw new RuntimeException(message)
      }
    }
  }

  /**
    * Adds a group of jobs to the job buffer
    *
    * @param jobs Group of jobs to be added.
    */
  def appendJobToBuffer(jobs: List[Job]): Unit = {
    dependencyManager.appendJobBuffer(jobs)
  }

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

  protected def getShouldBeWaitedOn: Boolean = {
    shouldBeWaitedOn
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
    * It changes the state of the status to Success, and tells the runManager to handle running the next job.
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
    * Checks the return counter to see if we are still waiting on jobs to return.
    *
    * @return True if still waiting on jobs to return, false otherwise
    */
  def waitingOnReturnJobs: Boolean = {
    // If returnCounter is over 0 not all the required dependencies have returned yet.
    returnCounter.getCount > 0
  }

  /**
    * Checks to make sure we aren't waiting on anymore jobs.
    * If we aren't, removes the first element of the jobBuffer and runs all jobs in that list.
    */
  def runNextJobIfReady(currentJob: Job): Unit = {
    if (waitingOnReturnJobs) return

    // Start next batch if exists
    if (jobBuffer.nonEmpty) {
      val head: List[Job] = jobBuffer.head
      jobBuffer -= head

      setFutureJobStates(head, currentJob)

      // Start all the jobs
      head.foreach(_.start())
    }
  }

  /**
    * This method checks if the current job has any more dependencies.
    * Dependencies are evaluated by if the job is still waiting on jobs to return and if it still has jobs in the job buffer.
    *
    * @return
    */
  def dependenciesExist(): Boolean = {
    returnCounter.getCount > 0 || jobBuffer.nonEmpty
  }

  /**
    * Adds all of the retry job's buffer and the retry job itself to the job manager,
    * then sets the current job that failed to run after that.
    *
    * Has a dedicated status, where status = 'Retry'
    *
    * Retry should only happen once, so we wipe it clean afterwards.
    */
  def runRetryJob(): Unit = {
    val someJob = retryJob.get

    // Add this job to the job manager
    JobManager.addJob(someJob)

    // Add the rest of the retry job buffer into the JobManager as they shouldn't be added if a retry doesn't happen
    someJob.internalState.dependencyManager.addDependenciesToJobManager()

    // Run the current job after this retry job goes through
    someJob.thenRun(job)
    someJob.internalState.setJobStatus(StatusCodes.Retry)

    // Remove retry job so it only retries once
    retryJob = None

    someJob.start()
  }

  def retryJob: Option[Job] = _retryJob

  def retryJob_=(value: Option[Job]): Unit = _retryJob = value

  /**
    * If dependencies still exist, launches those dependencies.
    *
    * Otherwise, notifies the job that launched it, if there was one, that it has completed.
    */
  def runNextJob(dependencyManager: DependencyManager): Unit = {
    if (dependencyManager.dependenciesExist()) {
      dependencyManager.runNextJobIfReady(job)
    } else {
      if (hasReturnJob) notifyReturnJobOfCompletion()
    }
  }
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

  def getJobStatus: String = synchronized {
    this.status
  }

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }


      /*
        The number of jobs that need to return to this job prior to it being able to keep going

        Jobs that are waited for should return to this job and also modify
        the returnCounter as a way of indicating their completeness.
       */
      val jobsToWaitFor = head.filter(_.getShouldBeWaitedOn)
      returnCounter.setCount(jobsToWaitFor.length)
      jobsToWaitFor.foreach(_.setReturnJob(this))

      // Start all the jobs
      head.foreach(_.start())
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
