package com.act.workflow.tool_manager.jobs.management

import com.act.workflow.tool_manager.jobs.Job
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


  /**
    * Removes all elements from the JobManager and resets the lock.
    */
  def clearManager(): Unit = {
    jobs = new ListBuffer[Job]()
    numberLock = new AtomicLatch()
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
    logger.info(s"Added command $job to JobManager")
    job
  }


  /**
    * To use futures, we need to keep our program alive by sleeping the thread and checking for it to complete.
    * The callbacks from the nonblocking job wrapper will add and remove from the list until hopefully no more jobs exist.
    *
    * Should be called at the end of a job instantiation set so that any long-running jobs will finish.
    *
    */
  def awaitUntilAllJobsComplete(): Unit = {
    require(jobs.nonEmpty, message = "Cannot await when no jobs have been started.  " +
      "Make sure to call start() on a job prior to awaiting.")
    instantiateCountDownLockAndWait()
  }

  /**
    * Blocking behaviour that invokes the lock and, when finished, displays the complete message.
    */
  private def instantiateCountDownLockAndWait() = {
    numberLock.await()
    logger.info("All jobs have completed.")
    logger.info(s"Number of jobs run = ${completedJobsCount()}")
    logger.info(s"Number of jobs added but not run = ${unstartedJobsCount()}")
    logger.info(s"Number of jobs failed = ${failedJobsCount()}")
    logger.info(s"Number of jobs successful = ${successfulJobsCount()}")
  }

  private def unstartedJobsCount(): Int = {
    jobs.count(x => x.isUnstarted)
  }

  private def failedJobsCount(): Int = {
    jobs.count(x => x.isFailed)
  }

  private def successfulJobsCount(): Int = {
    jobs.count(x => x.isSuccessful)
  }

  private def completedJobsCount(): Int = {
    jobs.count(x => x.isCompleted)
  }

  def indicateJobCompleteToManager() {
    numberLock.countDown()
    logger.info(s"<Concurrent jobs running = ${runningJobsCount()}>")
    logger.info(s"<Current jobs awaiting to run = ${waitingJobsCount()}>")
    logger.info(s"<Completed jobs = ${completedJobsCount()}>")
  }

  private def waitingJobsCount(): Int = {
    jobs.length - (completedJobsCount() + runningJobsCount())
  }

  private def runningJobsCount(): Int = {
    jobs.count(x => x.isRunning)
  }

  /*
    General job query questions that may be reused
  */
  private def allJobsComplete(): Boolean = {
    getIncompleteJobs.isEmpty
  }

  private def getIncompleteJobs: List[Job] = {
    jobs.filter(x => x.isRunning).toList
  }

  private def mapStatus: Map[String, Int] = {
    // Take the jobs, identify and count their statuses and return that count
    val allJobsStatuses: List[String] = jobs.map(jobs => jobs.getJobStatus).toList
    val jobsGroupedByIdentity: Map[String, List[String]] = allJobsStatuses.groupBy(identity)
    jobsGroupedByIdentity.mapValues(_.size)
  }
}
