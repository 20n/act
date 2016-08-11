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

  private val flags: ListBuffer[JobFlag.Value] = ListBuffer[JobFlag.Value]()

  /**
    * Adds a flag to this job, which designates that certain, advanced behavior should occur
    *
    * An example of behavior a flag might add is the flag ShouldNotFailChildrenJobs disables cascading failure of jobs
    * (Wherein a parent job fails and fails all children jobs).  Therefore, all the children of
    * that job will be run and only fail if they actually fail during a run, not in a preemptive manner as normal.
    *
    * @param value An allowed JobFlag
    */
  def addFlag(value: JobFlag.Value): Unit = flags.append(value)

    // Job manager should know if has been marked as complete
    if (isCompleted) JobManager.indicateJobCompleteToManager(this.name)
  }

  /*
    User description of job

  /**
    * Defined by the given type of job to effectively run asynchronously.
    */
  def asyncJob()

  /**
    * Run the async job and sets status to 'Running'
    */
  def start(): Unit = {
    // Killed jobs should never start
    status.isKilled match {
      case true => logger.debug("Attempted to start a killed job.")

      case false =>
        status.isNotStarted match {
          case true =>
            logger.trace(s"Started command ${this}")
            internalState.setJobStatus(StatusCodes.Running)
            asyncJob()

          case false =>
            val message = s"Attempted to start a job that has already been started.  " +
              s"Job name is $getName with current status ${internalState.statusManager.getJobStatus}"
            logger.fatal(message)
            throw new RuntimeException(message)
        }
    }
  }

  private def status: StatusManager = internalState.statusManager

  def runNextJob(): Unit = {
    internalState.runManager.runNextJob(internalState.dependencyManager)
  }

  def addCancelFunction(cancelFunction: () => Boolean): Unit = {
    internalState.setCancelCurrentJobFunction(cancelFunction)
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

  def getName: String = {
    this.name
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
  def appendJobToBuffer(job: Job): Unit = {
    dependencyManager.addDependency(job)
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
    * When a job wants to indicate it completed successfully, it calls this method.
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
    // The success is if the future succeeded.
    // We need to also check the return code and redirect to failure here if it completed, but with a bad return code
    setJobStatus(JobStatus.Success)
    runNextJob()
    handleIfJobTotallyComplete()
  }

  /**
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  def markRemainingDependenciesAsFailure(): Unit = {
    jobBuffer.foreach(
      columnOfJobs => columnOfJobs.foreach(
        individualJob => individualJob.internalState.setJobStatus(StatusCodes.ParentProcessFailure))
    )
  }

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

  /*
    Local job continuation utilities
  */

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
      returnJob.get.runNextJob()
    }
  }

  def isKilled: Boolean = {
    getJobStatus.equals(StatusCodes.Killed)
  }

  def isSuccessful: Boolean = {
    getJobStatus == StatusCodes.Success
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
  }

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }

  override def toString: String = {
    getJobStatus
  }
}
