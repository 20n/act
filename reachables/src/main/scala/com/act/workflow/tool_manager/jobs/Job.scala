package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.jobs.management.utility.AtomicLatch
import org.apache.logging.log4j.{LogManager, Logger}

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
    * Adds a flag to this job, which designates that certain, advanced behavior should occur
    *
    * An example of behavior a flag might add is the flag ShouldNotFailChildrenJobs disables cascading failure of jobs
    * (Wherein a parent job fails and fails all children jobs).  Therefore, all the children of
    * that job will be run and only fail if they actually fail during a run, not in a preemptive manner as normal.
    *
    * @param value An allowed JobFlag
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
    * This job will be run if the current job fails, prior to attempt to rerun the current job.
    *
    * The job given to retryJob is usually not run.  However, it will be added to the Job manager
    * and run if this job fails.  Then, after retryJob completes it will attempt to launch this job again.
    *
    * @param job job to run prior to retrying
    *
    * @return this, for chaining
    */
  def setJobToRunPriorToRetrying(job: Job): Job = {
    internalState.runManager.preRetryJob = job
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
    jobGroups.foreach(internalState.dependencyManager.addDependency)
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
    internalState.dependencyManager.addDependency(nextJob)
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

  def getReturnCode: Int = returnCode

  def setReturnCode(value: Int): Unit = returnCode = value

  def setCancelCurrentJobFunction(value: Option[() => Boolean]): Unit = cancelCurrentJobFunction = value

  def setPreRetryJob(value: Job): Unit = runManager.preRetryJob = Option(value)

  /**
    * Adds an individual job as a dependency of this job, meaning it should be run after this job finishes.
    *
    * @param job Job to be added
    */
  def start(): Unit = {
    if (JobStatus.Killed != getJobStatus) {
      logger.info(s"Started command ${this}")
      setJobStatus(JobStatus.Running)
      asyncJob()
    }
  }

  /**
    * Adds a group of jobs as a dependency of this job, meaning it should be run after this job finishes.
    *
    * @param jobs Group of jobs to be added.
    */
  def appendJobToBuffer(jobs: List[Job]): Unit = {
    dependencyManager.addDependency(jobs)
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

  /**
    * Usually called from an asyncJob when it has completed.
    *
    * It changes the state of the status to Success, and tells the runManager to handle running the next job.
    */
  def markAsSuccess(): Unit = {
    /*
      The success is if the future succeeded.
      We need to also check the return code and redirect to failure here if it completed, but with a bad return code
     */
    setJobStatus(StatusCodes.Success)
    runManager.runNextJob(dependencyManager)
  }

  /**
    * First checks if there is a retryJob available, in which case it attempts to run it.
    * Otherwise, simply marks this and all jobs after it as a failure.
    */
  def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    runManager.hasPreRetryJob match {
      case true =>
        // Retry jobs mean that we should first try to run other jobs prior to running this one.
        logger.error(s"Running retry job ${runManager.preRetryJob.get}. $this has encountered an error")
        this.setStatus(StatusCodes.Retry)
        runManager.runPreRetryJobPriorToRetrying()
      case false =>
        setJobStatus(StatusCodes.Failure)

        /*
          The flag should not fail children jobs even if the current job fails. Therefore we don't cascade a failure,
          but run each job in a way such the only failure that occurs is
          if a specific job fails after attempting to run.
         */
        job.getFlags.contains(JobFlag.ShouldNotFailChildrenJobs) match {
          case true => runManager.runNextJob(dependencyManager)
          case false =>
            if (runManager.hasReturnJob)
              runManager.getReturnJobDependencyManager.markRemainingDependenciesAsFailure()
            // Map this job's buffer as a failure
            dependencyManager.markRemainingDependenciesAsFailure()
        }
    }
  }

  /**
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
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
  def runNextJobIfReady(currentJob: Job): Unit = synchronized {
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

  def addDependenciesToJobManager(): Unit = {
    jobBuffer.foreach(
      jobTier => jobTier.foreach(
        jobAtTier => JobManager.addJob(jobAtTier)))
  }

  /**
    * Update the jobs that are going to be run in a given job batch.
    * The updates that will occur is to set the currentJob's returnCounter to the correct number
    * and also to tell jobs that don't have the `ShouldNotBeWaitedOn` flag which job to return to upon completion
    *
    * @param jobBatch   - The list of jobs about to be run
    * @param currentJob - The current job that these jobs will be run from
    */
  private def setFutureJobStates(jobBatch: List[Job], currentJob: Job): Unit = {
    /*
      The number of jobs that need to return to this job prior to it being able to keep going

      Jobs that are waited for should return to this job and also modify
      the returnCounter as a way of indicating their completeness.
     */
    val jobsToWaitFor: List[Job] = jobBatch.filter(x => !x.getFlags.contains(JobFlag.ShouldNotBeWaitedOn))
    returnCounter.setCount(jobsToWaitFor.length)

    // In some cases, all jobs we may not care about waiting on,
    // thus we should verify that we shouldn't start the next jobs here.
    if (!waitingOnReturnJobs) {
      runNextJobIfReady(currentJob)
    }

    // Tell the job we are about to launch that it should tell the job launching it when it is done.
    jobsToWaitFor.foreach(_.internalState.runManager.returnJob = currentJob)
  }
}

  /**
    * Adds all of the retry job's buffer and the pre-retry job itself to the job manager,
    * then sets the current job that failed to run after that.
    *
    * Has a dedicated status, where status = 'Retry'
    *
    * Retry should only happen once, so we wipe it clean afterwards.
    */
  def runPreRetryJobPriorToRetrying(): Unit = {
    val someJob = preRetryJob.get

    // Add this job to the job manager
    JobManager.addJob(someJob)

    // Add the rest of the retry job buffer into the JobManager as they shouldn't be added if a retry doesn't happen
    someJob.internalState.dependencyManager.addDependenciesToJobManager()

    // Run the current job after this retry job goes through
    someJob.thenRun(job)
    someJob.internalState.setJobStatus(StatusCodes.Retry)

    // Remove retry job so it only retries once
    preRetryJob = None

    someJob.start()
  }

  def preRetryJob: Option[Job] = _preRetryJob

  def preRetryJob_=(value: Option[Job]): Unit = _preRetryJob = value

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

  def isSuccessful: Boolean = {
    getJobStatus == StatusCodes.Success
  }

  def getJobStatus: String = synchronized {
    this.status
  }

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }

  def isFailed: Boolean = {
    getJobStatus.equals(StatusCodes.Failure) |
      getJobStatus.equals(StatusCodes.ParentProcessFailure) |
      getJobStatus.equals(StatusCodes.Killed)
  }

  def isNotStarted: Boolean = {
    getJobStatus == StatusCodes.NotStarted
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

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }

  override def toString: String = {
    getJobStatus
  }
}
