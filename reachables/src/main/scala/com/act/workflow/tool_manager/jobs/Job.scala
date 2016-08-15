package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.{AtomicLatch, JobManager, JobStatus, LoggingVerbosity}
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

abstract class Job(name: String) {
  protected val jobBuffer = ListBuffer[List[Job]]()
  /*
    How many jobs need to return to this one prior to it starting
    This is useful as then we can model sequential jobs in a job buffer with a list of jobs to run at each sequence.
   */
  protected val returnCounter = new AtomicLatch
  private val logger: Logger = LogManager.getLogger(getClass.getName)
  private val status = new JobStatus
  protected var jobReturnCode = -1
  protected var retryJob: Option[Job] = None
  protected var returnJob: Option[Job] = None
  protected var cancelFuture: Option[() => Boolean] = None

  private var verbosity = LoggingVerbosity.Medium
  /*
    If true, this job will wait for it to complete prior to launching next round of jobs.
    Otherwise, it will launch whenever all the other jobs it is waiting on are done.
   */
  private var shouldBeWaitedOn = true

  def setVerbosity(verbosity: Int): Unit = {
    require(verbosity >= LoggingVerbosity.Off && verbosity <= LoggingVerbosity.High,
      s"Verbosity must be set as an integer at or between ${LoggingVerbosity.Off} - ${LoggingVerbosity.High}")
    this.verbosity = verbosity
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

  def getJobStatus: JobStatus = {
    status
  }

  def getReturnCode: Int = {
    jobReturnCode
  }

  /**
    * Modifies the return code of this element
    *
    * @param returnCode Value given to the return code
    */
  def setReturnCode(returnCode: Int): Unit = {
    if (this.verbosity >= LoggingVerbosity.Medium_Low) logger.info(s"Command ${this} has changed return code to $returnCode")
    jobReturnCode = returnCode
  }

  def getJobBuffer: List[List[Job]] = {
    jobBuffer.toList
  }

  /**
    * If this is set, when this job is added to another job it won't report
    * back to that job that it is complete and the other job will continue even if it isn't complete.
    */
  def jobShouldNotBeWaitedFor() {
    shouldBeWaitedOn = false
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


  /* Adding job(s) to the run */

  /**
    * Adds a group of jobs as a dependency of this job, meaning it should be run after this job finishes.
    *
    * @param jobs Group of jobs to be added.
    */
  def appendJobToBuffer(jobs: List[Job]): Unit = {
    dependencyManager.addDependency(jobs)
  }

  override def toString: String = {
    s"[Job]<$getName>"
  }

  /**
    * Kills a job if it is currently not started.
    */
  def killUncompleteJob() {
    // Only kill jobs if they haven't completed already.
    if (!status.isCompleted) {
      // Currently running needs to cancel future.
      if (this.cancelFuture.isDefined) {
        this.cancelFuture.get()
      }

      setJobStatus(status.StatusCodes.Killed)
      jobBuffer.foreach(jobTier => jobTier.foreach(
        jobAtTier => {
          jobAtTier.setJobStatus(status.StatusCodes.Killed)
          jobAtTier.killUncompleteJob()
        }))
    }
  }

  protected def getShouldBeWaitedOn: Boolean = {
    shouldBeWaitedOn
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
    setJobStatus(status.StatusCodes.Success)
    handleIfJobTotallyComplete()
    runNextJobIfReady()
  }

  /**
    * First checks if there is a retryJob available, in which case it attempts to run it.
    * Otherwise, simply marks this and all jobs after it as a failure.
    */
  protected def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    if (retryJob.isDefined) {
      if (verbosity >= LoggingVerbosity.Low) logger.info(s"Running retry job ${retryJob.get}. ${this} has encountered an error")
      runRetryJob()
    } else {
      setJobStatus(status.StatusCodes.Failure)

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
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  protected def markJobsAfterThisAsFailure(): Unit = {

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
    someJob.thenRun(this)
    setJobStatus(status.StatusCodes.Retry)

    // Remove retry job so it only retries once
    preRetryJob = None

    someJob.start()
  }

  def preRetryJob: Option[Job] = _preRetryJob

  def preRetryJob_=(value: Option[Job]): Unit = _preRetryJob = value

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

  /**
    * Run the async job and sets status to 'Running'
    */
  def start(): Unit = {
    // Killed jobs should never start
    if (!status.isKilled) {
      if (status.isUnstarted) {
        if (verbosity >= LoggingVerbosity.Low)
          logger.info(s"Started command ${this}")
        setJobStatus(status.StatusCodes.Running)
        asyncJob()
      } else {
        val message = s"Attempted to start a job that has already been started.  " +
          s"Job name is $getName with current status ${status.getJobStatus}"
        if (verbosity >= LoggingVerbosity.Off) {
          logger.error(message)
        }
        throw new RuntimeException(message)
      }
    }
  }

  /*
    Local job continuation utilities
  */

  /**
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
    */
  protected def setJobStatus(newStatus: String): Unit = {
    val message = s"Job status for command ${this} has changed to $newStatus"
    newStatus match {
      case s if status.StatusCodes.ParentProcessFailure.equals(s) =>
        if (verbosity >= LoggingVerbosity.High) logger.info(message)
      case s if status.StatusCodes.Killed.equals(s) =>
        if (verbosity >= LoggingVerbosity.Medium_High) logger.info(message)
      case default =>
        if (verbosity >= LoggingVerbosity.Medium) logger.info(message)
    }
    status.setJobStatus(newStatus)

    // Job manager should know if has been marked as complete
    if (status.isCompleted) {
      JobManager.indicateJobCompleteToManager(this)
    }
  }

  def getName: String = {
    this.name
  }

  /**
    * Modify which job this job should call back to after finishing
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
    // If returnCounter is over 0 not all the required dependencies have returned yet.
    if (returnCounter.getCount > 0) {
      return
    }

  def isSuccessful: Boolean = {
    getJobStatus == StatusCodes.Success
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
}
