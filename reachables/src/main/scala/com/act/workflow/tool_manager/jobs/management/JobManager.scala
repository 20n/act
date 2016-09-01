package com.act.workflow.tool_manager.jobs.management

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.utility.{AtomicLatch, LoggingController}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

/**
  * Manages all job processes and takes care of logging and blocking program exit
  */
object JobManager {
  // General logger which can be used outside of this class too
  private val logger = LogManager.getLogger(getClass.getName)
  // Can be used to ensure completion of all jobs w/ awaitUntilAllJobsComplete()
  private var jobs = new ListBuffer[Job]()
  // Lock for job manager
  private var numberLock = new AtomicLatch()

  private var jobCompleteOrdering = new ListBuffer[Job]

  private var jobToAwaitFor: Option[Job] = None

  def setVerbosity(verbosity: Int): Unit = {
    require(verbosity >= 0 && verbosity <= 6, "Verbosity must be set as an integer at or between 0 - 6.")
    LoggingController.setVerbosity(LoggingController.verbosityMap(verbosity))
    logger.info(s"Verbosity was changed to level $verbosity")
  }

  /**
    * Removes all elements from the JobManager and resets the lock.
    */
  def clearManager(): Unit = {
    jobToAwaitFor = None
    jobs = new ListBuffer[Job]()
    numberLock = new AtomicLatch()
    jobCompleteOrdering = new ListBuffer[Job]
  }

  /**
    * Adds a job to the manager and updates the lock information regarding it
    *
    * @param job Job to be added to the tracking
    *
    * @return the job added
    */
  def addJob(job: Job): Job = {
    jobs.append(job)
    numberLock.countUp()
    logger.debug(s"Added command $job to JobManager")
    job
  }

  /**
    * To use futures, we need to keep our program alive by sleeping the thread and checking for it to complete.
    * The callbacks from the nonblocking job wrapper will add and remove from the list until hopefully no more jobs exist.
    *
    * Should be called at the end of a job instantiation set so that any long-running jobs will finish.
    *
    */
  def startJobAndAwaitUntilWorkflowComplete(firstJob: Job): Unit = {
    require(jobs.nonEmpty, message = "Cannot await when no jobs have been started.  " +
      "Make sure to call start() on a job prior to awaiting.")

    verifyAllJobsAreReachableAndNoCycles(firstJob)
    firstJob.start()
    instantiateCountDownLockAndWait()
  }

  def startJobAndKillWorkflowAfterSpecificJobCompletes(firstJob: Job, jobToWaitFor: Job): Unit = {
    require(jobs.nonEmpty, message = "Cannot await when no jobs have been started.  " +
      "Make sure to call start() on a job prior to awaiting.")
    verifyAllJobsAreReachableAndNoCycles(firstJob)

    this.jobToAwaitFor = Option(jobToWaitFor)
    firstJob.start()
    instantiateCountDownLockAndWait()
  }

  def indicateJobCompleteToManager(job: Job) {
    if (!jobs.contains(job)) {
      /*
        This job doesn't currently exist in the known jobs but for some reason is hitting the manager.
        Likely means an old or incorrectly run job so we log the error and move on, but don't let it change things.
        Given our kill behavior this should not happen, but just in case we have the check below.
       */
      val message = s"A job $job that doesn't exist in job buffer tried to modify Job Manager."
      logger.error(message)
      return
    }

    // If we are waiting for a job and find that job, release the number lock
    if (jobToAwaitFor.isDefined) {
      if (job.equals(jobToAwaitFor.get)) {
        // Cancel all futures still running.  If we kill the jobs here, we don't have to worry about the
        // time difference between the lock releasing and us handling those
        // conditions normally and another job starting in the meantime.
        jobs.foreach(_.internalState.killIncompleteJobs())
        numberLock.releaseLock()
      }
    } else {
      numberLock.countDown()
    }
    jobCompleteOrdering.append(job)

    logger.trace(s"<Concurrent jobs running = ${runningJobsCount()}>")
    logger.trace(s"<Current jobs awaiting to run = ${waitingJobsCount()}>")
    logger.trace(s"<Completed jobs = ${completedJobsCount()}>")
  }

  private def waitingJobsCount(): Int = {
    jobs.length - (completedJobsCount() + runningJobsCount())
  }

  def completedJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isCompleted)
  }

  private def runningJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isRunning)
  }

  def getMapOfJobNamesToStatuses: Map[String, String] = {
    (getOrderOfJobCompletion zip getOrderOfJobStatuses) toMap
  }

  def getOrderOfJobCompletion: List[String] = {
    jobCompleteOrdering.toList.map(x => x.getName)
  }

  def getOrderOfJobStatuses: List[String] = {
    jobCompleteOrdering.toList.map(_.internalState.statusManager.getJobStatus)
  }

  /**
    * Blocking behaviour that invokes the lock and, when finished, displays the complete message.
    */
  private def instantiateCountDownLockAndWait() = {
    numberLock.await()

    logger.info("All jobs have completed.")
    logger.info(s"Number of jobs run = ${completedJobsCount()}")
    logger.info(s"Number of jobs added but not run = ${unstartedJobsCount()}")
    logger.info(s"Number of jobs killed = ${killedJobsCount()}")
    logger.info(s"Number of jobs failed = ${failedJobsCount()}")
    logger.info(s"Number of jobs successful = ${successfulJobsCount()}")
  }

  private def killedJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isKilled)
  }

  private def unstartedJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isNotStarted)
  }

  private def failedJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isFailed)
  }

  private def successfulJobsCount(): Int = {
    jobs.count(_.internalState.statusManager.isSuccessful)
  }

  /**
    * Goes through and ensures all jobs in `jobs` are reachable, and thus will be run in a normal, successful workflow.
    *
    * @param job The first job
    */
  private def verifyAllJobsAreReachableAndNoCycles(job: Job): Unit = {
    // It is important to verify cycles first as otherwise reachables search will never end ~
    val allJobs: Set[Job] = getAllChildren(job)

    // Grab all the jobs that can't be reached so we can report back which ones need to be connected
    val jobsNotReached = jobs.filter(job => !allJobs.contains(job))
    if (jobsNotReached.nonEmpty) {
      throw new RuntimeException("Started waiting for jobs, but program is unable to reach all jobs.  " +
        s"Please ensure your workflow is fully connected.  " +
        s"The jobs that are not connected are ${jobsNotReached.map(_.getName)}")
    }
  }

  /**
    * Checks that the number of unique jobs we can reach and
    * the number of jobs we can reach are the same, thus showing that no cycles exist.
    *
    * This also verifies that no job will be run twice, as that would also cause the program to fail.
    *
    * @param job Job to start looking from
    *
    * @return A set of all jobs.  If a cycle is detected raises an error.
    */
  private def getAllChildren(job: Job, currentJobList: ListBuffer[Job] = new ListBuffer[Job]()): Set[Job] = {
    currentJobList.append(job)

    // Found a cycle, raise error
    if (currentJobList.toSet.size != currentJobList.length) {
      throw new RuntimeException("Detected an abnormality in your workflow.  " +
        "Either a cycle existsthe same job occurs multiple times in your workflow, either of which are not allowed.  " +
        "In either of these scenario, your workflow will be unable to run the job a second time and thus will crash. " +
        "Please review your workflow.")
    }

    for (jobLevel <- job.internalState.dependencyManager.jobBuffer.toList) {
      for (currentJob <- jobLevel) {
        // If a cycle found below, return true to propagate.
        getAllChildren(currentJob, currentJobList)
      }
    }

    currentJobList.toSet
  }
}