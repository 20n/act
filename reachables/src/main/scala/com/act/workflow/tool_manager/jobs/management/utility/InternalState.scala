package com.act.workflow.tool_manager.jobs.management.utility

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.JobManager
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

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

  def setRetryJob(value: Job): Unit = {
    runManager.retryJob = Option(value)
  }

  def appendJobToBuffer(job: Job): Unit = {
    dependencyManager.appendJobBuffer(job)
  }

  def appendJobToBuffer(jobs: List[Job]): Unit = {
    dependencyManager.appendJobBuffer(jobs)
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
    * A consistent source to change the job status.
    * Has the added benefit of notifying the JobManager if that status changes to complete.
    *
    * @param newStatus What new status should be assigned to the job
    */
  def setJobStatus(newStatus: String): Unit = {
    // Handle Logging of various levels
    val message = s"Job status for command $job has changed to $newStatus"
    newStatus match {
      case s if StatusCodes.ParentProcessFailure.equals(s) => logger.debug(message)
      case s if StatusCodes.Killed.equals(s) => logger.debug(message)
      case default => logger.info(message)
    }

    statusManager.setJobStatus(newStatus)

    // Job manager should know if has been marked as complete
    if (statusManager.isCompleted) JobManager.indicateJobCompleteToManager(job)
  }

  /**
    * First checks if there is a retryJob available, in which case it attempts to run it.
    * Otherwise, simply marks this and all jobs after it as a failure.
    */
  def markAsFailure(): Unit = {
    // If a retry job exists, we run it otherwise the job has failed and any subsequent jobs fail because of this
    runManager.hasRetryJob match {
      case true =>
        logger.error(s"Running retry job ${runManager.retryJob.get}. ${this} has encountered an error")
        runManager.runRetryJob()
      case false =>
        setJobStatus(StatusCodes.Failure)

        /*
          The flag should not fail children jobs means that we don't cascade a failure,
          but run each job in a way such the only failure state is natural.
         */
        job.getFlags.contains(JobFlag.ShouldNotFailChildrenJobs) match {
          case true => runManager.runNextJob(dependencyManager)
          case false =>
            if (runManager.hasReturnJob) runManager.getReturnJobDependencyManager.markRemainingDependenciesAsFailure()
            // Map this job's buffer as a failure
            dependencyManager.markRemainingDependenciesAsFailure()
        }
    }
  }

  /**
    * Kills a job if it is not yet complete (Either unstarted or running)
    */
  def killIncompleteJobs() {
    // Only kill jobs if they haven't completed already.
    if (!statusManager.isCompleted) {

      // Currently running needs to cancel future.
      if (getCancelCurrentJobFunction.isDefined) {
        // Get and execute the function
        val cancelFunction: () => Boolean = getCancelCurrentJobFunction.get
        cancelFunction()
      }

      setJobStatus(StatusCodes.Killed)

      dependencyManager.killDependencies()
    }
  }

  def getCancelCurrentJobFunction: Option[() => Boolean] = cancelCurrentJobFunction

  def setCancelCurrentJobFunction(value: () => Boolean): Unit = cancelCurrentJobFunction = Option(value)
}

/**
  * Keeps track of any dependencies this job may have,
  * in regards to jobs that it is tasked with running after itself and handling their completion status.
  */
class DependencyManager {
  val jobBuffer = ListBuffer[List[Job]]()
  val returnCounter = new AtomicLatch

  def appendJobBuffer(job: Job): Unit = appendJobBuffer(List(job))

  def appendJobBuffer(jobs: List[Job]): Unit = jobBuffer.append(jobs)

  def killDependencies(): Unit = {
    jobBuffer.foreach(jobTier => jobTier.foreach(
      jobAtTier => {
        jobAtTier.internalState.killIncompleteJobs()
      }))
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

  def forwardDependenciesExist(): Boolean = {
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
  * Takes care of handling the transition state between two jobs,
  * wherein one job launches another or tells it that it has completed.
  *
  * @param job - The job to manage
  */
class RunManager(job: Job) {
  private var _retryJob: Option[Job] = None
  private var _returnJob: Option[Job] = None

  def retryJob_=(value: Job): Unit = _retryJob = Option(value)

  def hasRetryJob: Boolean = _retryJob.isDefined

  def returnJob: Option[Job] = _returnJob

  def returnJob_=(value: Option[Job]): Unit = _returnJob = value

  def returnJob_=(value: Job): Unit = _returnJob = Option(value)

  def hasReturnJob: Boolean = _returnJob.isDefined

  def getReturnJobRunManager: RunManager = _returnJob.get.internalState.runManager

  def getReturnJobDependencyManager: DependencyManager = returnJob.get.internalState.dependencyManager

  /**
    * Modifies the return job's counter and then asks it to continue running any jobs that it still has.
    *
    * The returnJob will sort out if it has any more jobs to wait on.
    */
  def notifyReturnJobOfCompletion(): Unit = {
    // Decrease return number
    getReturnJobDependencyManager.returnCounter.countDown()
    getReturnJobRunManager.runNextJob(getReturnJobDependencyManager)
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
    if (dependencyManager.forwardDependenciesExist()) {
      dependencyManager.runNextJobIfReady(job)
    } else {
      if (hasReturnJob) notifyReturnJobOfCompletion()
    }
  }
}

/**
  * Update and query job statuses
  */
object StatusCodes extends Enumeration {
  type Status = Value
  val Success = "Success"
  val Retry = "Retrying"
  val Failure = "Failure"
  val Running = "Running"
  val NotStarted = "NotStarted"
  val ParentProcessFailure = "Parent Process Failed"
  val Killed = "Killed"
}

/**
  * Keeps track of the status of the job.
  */
class StatusManager {
  private var status = StatusCodes.NotStarted

  def isCompleted: Boolean = {
    isSuccessful | isFailed | isKilled
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

  def isRunning: Boolean = {
    getJobStatus == StatusCodes.Running
  }

  def getJobStatus: String = synchronized {
    this.status
  }

  def setJobStatus(newStatus: String): Unit = synchronized {
    this.status = newStatus
  }

  override def toString: String = {
    getJobStatus
  }
}