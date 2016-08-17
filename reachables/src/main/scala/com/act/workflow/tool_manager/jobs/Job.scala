package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.jobs.management.utility.{AtomicLatch, JobFlag, JobStatus, StatusCodes}
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

class JobInternalStatus(job: Job) {
  private val logger: Logger = LogManager.getLogger(getClass.getName)

  private val _flags = ListBuffer[JobFlag.Value]()
  private val _jobBuffer = ListBuffer[List[Job]]()
  private val _returnCounter = new AtomicLatch
  private val _status = new JobStatus
  private var _returnCode: Int = -1
  private var _returnJob: Option[Job] = None
  private var _cancelFuture: Option[() => Boolean] = None
  private var _retryJob: Option[Job] = None

  def flags: List[JobFlag.Value] = _flags.toList

  def addFlagToJob(flag: JobFlag.Value): Unit = _flags.append(flag)

  def jobBuffer: ListBuffer[List[Job]] = _jobBuffer

  def appendJobBuffer(job: Job): Unit = appendJobBuffer(List(job))

  def appendJobBuffer(jobs: List[Job]): Unit = _jobBuffer.append(jobs)

  def returnCounter: AtomicLatch = _returnCounter

  def returnCode: Int = _returnCode

  def returnCode_=(value: Int): Unit = _returnCode = value

  def returnJob: Option[Job] = _returnJob

  def returnJob_=(value: Option[Job]): Unit = _returnJob = value

  def cancelFuture: Option[() => Boolean] = _cancelFuture

  def cancelFuture_=(value: Option[() => Boolean]): Unit = _cancelFuture = value

  def retryJob: Option[Job] = _retryJob

  def retryJob_=(value: Option[Job]): Unit = _retryJob = value

  def status: JobStatus = _status

  def status_=(value: String): Unit = _status.setJobStatus(value)

  def handleIfJobTotallyComplete(): Unit = {
    if (returnJob.isDefined && returnCounter.getCount <= 0 && jobBuffer.length <= 0) {
      // Decrease return number
      returnJob.get.internalState.returnCounter.countDown()
      returnJob.get.internalState.handleIfJobTotallyComplete()

      // Try to start it again and let it handle if it should
      returnJob.get.internalState.runNextJobIfReady()
    }
  }

  /**
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  def markJobsAfterThisAsFailure(): Unit = {
    jobBuffer.foreach(
      columnOfJobs => columnOfJobs.foreach(
        individualJob => individualJob.internalState.setJobStatus(StatusCodes.ParentProcessFailure))
    )
  }

  /**
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
    */
  def setJobStatus(newStatus: String): Unit = {
    val message = s"Job status for command $job has changed to $newStatus"
    newStatus match {
      case s if StatusCodes.ParentProcessFailure.equals(s) => logger.debug(message)
      case s if StatusCodes.Killed.equals(s) => logger.debug(message)
      case default => logger.info(message)
    }
    status.setJobStatus(newStatus)

    // Job manager should know if has been marked as complete
    if (status.isCompleted) {
      JobManager.indicateJobCompleteToManager(job)
    }
  }

  /**
    * Kills a job if it is not yet complete (Either unstarted or running)
    */
  def killIncompleteJobs() {
    // Only kill jobs if they haven't completed already.
    if (!status.isCompleted) {
      // Currently running needs to cancel future.
      if (cancelFuture.isDefined) {
        // Get and execute the function
        val cancelFunction: () => Boolean = cancelFuture.get
        cancelFunction()
      }

      setJobStatus(StatusCodes.Killed)
      jobBuffer.foreach(jobTier => jobTier.foreach(
        jobAtTier => {
          jobAtTier.internalState.setJobStatus(StatusCodes.Killed)
          jobAtTier.internalState.killIncompleteJobs()
        }))
    }
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
  def markAsSuccess(): Unit = {
    /*
      The success is if the future succeeded.
      We need to also check the return code and redirect to failure here if it completed, but with a bad return code
     */
    setJobStatus(StatusCodes.Success)
    handleIfJobTotallyComplete()
    runNextJobIfReady()
  }

  /**
    * First checks if there is a retryJob available, in which case it attempts to run it.
    * Otherwise, simply marks this and all jobs after it as a failure.
    */
  def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    if (retryJob.isDefined) {
      logger.error(s"Running retry job ${retryJob.get}. ${this} has encountered an error")
      runRetryJob()
    } else {
      setJobStatus(StatusCodes.Failure)

      if (flags.contains(JobFlag.ShouldNotFailChildrenJobs)) {
        handleIfJobTotallyComplete()
        runNextJobIfReady()
      } else {
        // Mark any jobs still in the buffer as ParentProcessFailure
        if (returnJob.isDefined) returnJob.get.internalState.markJobsAfterThisAsFailure()

        // Map this job's buffer as a failure
        this.markJobsAfterThisAsFailure()
      }

    }
  }

  /**
    * Checks to make sure we aren't waiting on anymore jobs.
    * If we aren't, removes the first element of the jobBuffer and runs all jobs in that list.
    */
  def runNextJobIfReady(): Unit = {
    // If returnCounter is over 0 not all the required dependencies have returned yet.
    if (returnCounter.getCount > 0) {
      return
    }

    // Start next batch if exists
    if (jobBuffer.nonEmpty) {
      val head = jobBuffer.head
      jobBuffer -= head

      /*
        The number of jobs that need to return to this job prior to it being able to keep going

        Jobs that are waited for should return to this job and also modify
        the returnCounter as a way of indicating their completeness.
       */
      val jobsToWaitFor = head.filter(!_.internalState.flags.contains(JobFlag.ShouldNotBeWaitedOn))
      returnCounter.setCount(jobsToWaitFor.length)
      jobsToWaitFor.foreach(_.internalState.returnJob = Option(job))

      // Start all the jobs
      head.foreach(_.start())
    }
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
    someJob.internalState.jobBuffer.foreach(jobTier => jobTier.foreach(jobAtTier => JobManager.addJob(jobAtTier)))

    // Run the current job after this retry job goes through
    someJob.thenRun(job)
    setJobStatus(StatusCodes.Retry)

    // Remove retry job so it only retries once
    retryJob = None

    someJob.start()
  }
}

/**
  * Job just defines the API
  *
  * To figure out what's up: https://github.com/20n/act/wiki/Scala-Workflows
  *
  * @param name - String name of the workflow
  */
abstract class Job(name: String) {
  private val logger: Logger = LogManager.getLogger(getClass.getName)
  private val _internalState = new JobInternalStatus(this)

  /**
    * Defined by the given type of job to effectively run asynchronously.
    */
  def asyncJob()

  /**
    * Run the async job and sets status to 'Running'
    */
  def start(): Unit = {
    // Killed jobs should never start
    if (!internalState.status.isKilled) {
      if (internalState.status.isUnstarted) {
        logger.trace(s"Started command ${this}")
        internalState.setJobStatus(StatusCodes.Running)
        asyncJob()
      } else {
        val message = s"Attempted to start a job that has already been started.  " +
          s"Job name is $getName with current status ${internalState.status.getJobStatus}"
        logger.fatal(message)
        throw new RuntimeException(message)
      }
    }
  }

  /* Adding job(s) to the run */

  def getName: String = {
    this.name
  }

  /*
    How many jobs need to return to this one prior to it starting
    This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
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
  def setJobToRunPriorToRetry(job: Job): Job = {
    internalState.retryJob = Option(job)
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
    jobGroups.foreach(x => internalState.appendJobBuffer(x))
    this
  }

  def internalState: JobInternalStatus = _internalState

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
    internalState.appendJobBuffer(nextJob)
    this
  }

  override def toString: String = {
    s"[Job]<$getName>"
  }
}
