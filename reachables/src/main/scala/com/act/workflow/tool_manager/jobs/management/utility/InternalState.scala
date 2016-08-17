package com.act.workflow.tool_manager.jobs.management.utility

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.JobManager
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

class InternalState(job: Job) {
  private val logger: Logger = LogManager.getLogger(getClass.getName)

  private val _runManager = new RunManager(job)
  private val _status = new Status
  private var _returnCode: Int = -1
  private var _cancelCurrentJobFunction: Option[() => Boolean] = None

  def returnCode: Int = _returnCode

  def returnCode_=(value: Int): Unit = _returnCode = value

  def cancelCurrentJobFunction_=(value: Option[() => Boolean]): Unit = _cancelCurrentJobFunction = value

  def setRetryJob(value: Job): Unit = {
    runManager.retryJob = Option(value)
  }

  def appendJobToBuffer(job: Job): Unit = {
    runManager.dependencyManager.appendJobBuffer(job)
  }

  def appendJobToBuffer(jobs: List[Job]): Unit = {
    runManager.dependencyManager.appendJobBuffer(jobs)
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
    runManager.runNextJob()
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

        // The flag should not fail children jobs means that we don't cascade a failure,
        // but run each job in a way such the only failure state is natural.
        job.getFlags.contains(JobFlag.ShouldNotFailChildrenJobs) match {
          case true => runManager.runNextJob()
          case false =>
            if (runManager.hasReturnJob) {
              runManager.returnJobDependencyManager.markRemainingDependenciesAsFailure()
            }

            // Map this job's buffer as a failure
            runManager.dependencyManager.markRemainingDependenciesAsFailure()
        }
    }
  }

  def runManager: RunManager = _runManager

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

    status.setJobStatus(newStatus)

    // Job manager should know if has been marked as complete
    if (status.isCompleted) {
      JobManager.indicateJobCompleteToManager(job)
    }
  }

  def status: Status = _status

  def status_=(value: String): Unit = _status.setJobStatus(value)

  /**
    * Kills a job if it is not yet complete (Either unstarted or running)
    */
  def killIncompleteJobs() {
    // Only kill jobs if they haven't completed already.
    if (!status.isCompleted) {

      // Currently running needs to cancel future.
      if (cancelCurrentJobFunction.isDefined) {
        // Get and execute the function
        val cancelFunction: () => Boolean = cancelCurrentJobFunction.get
        cancelFunction()
      }

      setJobStatus(StatusCodes.Killed)

      runManager.dependencyManager.killDependencies()
    }
  }

  def cancelCurrentJobFunction: Option[() => Boolean] = _cancelCurrentJobFunction

  def cancelCurrentJobFunction_=(value: () => Boolean): Unit = _cancelCurrentJobFunction = Option(value)
}

class DependencyManager {
  private val _jobBuffer = ListBuffer[List[Job]]()
  private val _returnCounter = new AtomicLatch

  def appendJobBuffer(job: Job): Unit = appendJobBuffer(List(job))

  def appendJobBuffer(jobs: List[Job]): Unit = _jobBuffer.append(jobs)

  def killDependencies(): Unit = {
    jobBuffer.foreach(jobTier => jobTier.foreach(
      jobAtTier => {
        jobAtTier.getInternalState.killIncompleteJobs()
      }))
  }

  /**
    * Grabs all the remaining job buffer lists, and then also all of their elements,
    * and assigns the value of 'ParentProcessFailure' to them based on the fact that their parent failed.
    */
  def markRemainingDependenciesAsFailure(): Unit = {
    jobBuffer.foreach(
      columnOfJobs => columnOfJobs.foreach(
        individualJob => individualJob.getInternalState.setJobStatus(StatusCodes.ParentProcessFailure))
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
      val head = getNextJobBatch

      setFutureJobStates(head, currentJob)

      // Start all the jobs
      head.foreach(_.start())
    }
  }

  def forwardDependenciesExist(): Boolean = {
    returnCounter.getCount > 0 || jobBuffer.nonEmpty
  }

  def returnCounter: AtomicLatch = _returnCounter

  def addDependenciesToJobManager(): Unit = {
    jobBuffer.foreach(
      jobTier => jobTier.foreach(
        jobAtTier => JobManager.addJob(jobAtTier)))
  }

  def jobBuffer: ListBuffer[List[Job]] = _jobBuffer

  private def getNextJobBatch: List[Job] = {
    val head: List[Job] = jobBuffer.head
    jobBuffer -= head
    head
  }

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

    jobsToWaitFor.foreach(
      x => x.getInternalState.runManager.returnJob = Option(currentJob)
    )
  }
}

class RunManager(job: Job) {
  private val _dependencyManager = new DependencyManager
  private var _retryJob: Option[Job] = None
  private var _returnJob: Option[Job] = None

  def retryJob_=(value: Job): Unit = _retryJob = Option(value)

  def hasRetryJob: Boolean = _retryJob.isDefined

  def returnJob: Option[Job] = _returnJob

  def returnJob_=(value: Option[Job]): Unit = _returnJob = value

  def returnJob_=(value: Job): Unit = _returnJob = Option(value)

  def hasReturnJob: Boolean = _returnJob.isDefined

  def returnJobRunManager: RunManager = _returnJob.get.getInternalState.runManager

  def returnJobDependencyManager: DependencyManager = getJobDependencyManager(_returnJob.get)

  def notifyReturnJobOfCompletion(): Unit = {
    // Decrease return number
    returnJobDependencyManager.returnCounter.countDown()
    returnJobRunManager.runNextJob()
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
    getJobDependencyManager(someJob).addDependenciesToJobManager()

    // Run the current job after this retry job goes through
    someJob.thenRun(job)
    someJob.getInternalState.setJobStatus(StatusCodes.Retry)

    // Remove retry job so it only retries once
    retryJob = None

    someJob.start()
  }

  def retryJob: Option[Job] = _retryJob

  def retryJob_=(value: Option[Job]): Unit = _retryJob = value

  private def getJobDependencyManager(job: Job): DependencyManager = {
    job.getInternalState.runManager.dependencyManager
  }

  def dependencyManager: DependencyManager = _dependencyManager

  def runNextJob(): Unit = {
    if (dependencyManager.forwardDependenciesExist()) {
      dependencyManager.runNextJobIfReady(job)
    } else {
      if (hasReturnJob) notifyReturnJobOfCompletion()
    }
  }
}

/*
Update and query job status
*/
object StatusCodes extends Enumeration {
  type Status = Value
  val Success = "Success"
  val Retry = "Retrying"
  val Failure = "Failure"
  val Running = "Running"
  val Unstarted = "Unstarted"
  val ParentProcessFailure = "Parent Process Failed"
  val Killed = "Killed"
}

class Status {
  private var status = StatusCodes.Unstarted

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

  def isUnstarted: Boolean = {
    getJobStatus == StatusCodes.Unstarted
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