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

  /**
    * Adds a flag to this job, which designates that certain, advanced behavior should occur
    *
    * @param value An allowed JobFlag
    */
  def addFlag(value: JobFlag.Value): Unit = flags.append(value)

  /**
    * Returns a list of all flags that this job possesses
    *
    * @return List of flags
    */
  def getFlags: List[JobFlag.Value] = flags.toList

  def preRetryJob: Option[Job] = _preRetryJob

  def preRetryJob_=(value: Option[Job]): Unit = _preRetryJob = value

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

  def getName: String = {
    this.name
  }

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
  def setJobToRunPriorToRetry(job: Job): Job = {
    internalState.runManager.retryJob = job
    this
  }
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
  def appendJobToBuffer(job: Job): Unit = {
    dependencyManager.appendJobBuffer(job)
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
    * When a job wants to indicate it completed successfully, it calls this method.
    *
    * It changes the state of the status to Success, and tells the runManager to handle running the next job.
    */
  protected def markAsSuccess(): Unit = {
    // The success is if the future succeeded.
    // We need to also check the return code and redirect to failure here if it completed, but with a bad return code
    setJobStatus(JobStatus.Success)
    handleIfJobTotallyComplete()
    runNextJob()
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
    if (returnJob.isDefined && returnCounter.getCount <= 0) {
      // Decrease return number
      returnJob.get.decreaseReturnCount()

      // Try to start it again and let it handle if it should
      returnJob.get.runNextJob()
    }
  }

  def isKilled: Boolean = {
    getJobStatus.equals(StatusCodes.Killed)
  }

  def getJobStatus: String = synchronized {
    this.status
  }

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }

  def isSuccessful: Boolean = {
    getJobStatus == StatusCodes.Success
  }

  def isFailed: Boolean = {
    getJobStatus.equals(StatusCodes.Failure) |
      getJobStatus.equals(StatusCodes.ParentProcessFailure) |
      getJobStatus.equals(StatusCodes.Killed)
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
