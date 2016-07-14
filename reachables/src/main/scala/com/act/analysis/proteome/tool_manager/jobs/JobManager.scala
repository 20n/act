package com.act.analysis.proteome.tool_manager.jobs

import org.apache.logging.log4j.LogManager
import org.hsqldb.lib.CountUpDownLatch

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
  private var lock = new CountUpDownLatch()

  def clearManager(): Unit ={
    jobs = new ListBuffer[Job]()
    lock = new CountUpDownLatch()
  }

  def addJob(job: Job): Job = {
    jobs.append(job)
    lock.countUp()
    logger.info(s"Added command ${job} to JobManager")
    job
  }


  /**
    * To use futures, we need to keep our program alive by sleeping the thread and checking for it to complete.
    * The callbacks from the nonblocking job wrapper will add and remove from the list until hopefully no more jobs exist.
    *
    * Should be called at the end of a job instantiation set so that any long-running jobs will finish.
    *
    * Default duration is checking every 10 seconds, sleepDuration option allows this to be changed.
    */
  def awaitUntilAllJobsComplete(): Unit = {
    require(jobs.length > 0, message="Cannot await when no jobs have been started.  " +
      "Make sure to call start() on a job prior to awaiting.")
    instantiateCountDownLockAndWait()
  }

  private def instantiateCountDownLockAndWait() = {
    lock.await()

    indicateCompleteStatus()
  }

  private def indicateCompleteStatus() = {
    logger.info("All jobs have completed.")
    logger.info(s"Number of jobs run = ${completedJobsCount()}")
    logger.info(s"Number of jobs added but not run = ${unstartedJobsCount()}")
    logger.info(s"Number of jobs failed = ${failedJobsCount()}")
    logger.info(s"Number of jobs successful = ${successfulJobsCount()}")
  }

  private def unstartedJobsCount(): Int = {
    jobs.count(x => x.isUnstarted())
  }

  private def failedJobsCount(): Int = {
    jobs.count(x => x.isFailed())
  }

  private def successfulJobsCount(): Int = {
    jobs.count(x => x.isSuccessful())
  }

  def indicateJobCompleteToManager() {
    lock.countDown()
    logger.info(s"<Concurrent jobs running = $runningJobsCount>")
    logger.info(s"<Current jobs awaiting to run = $waitingJobsCount>")
    logger.info(s"<Completed jobs = $completedJobsCount>")
  }

  private def waitingJobsCount(): Int = {
    jobs.length - (completedJobsCount() + runningJobsCount())
  }

  private def completedJobsCount(): Int = {
    jobs.count(x => x.isCompleted())
  }

  private def runningJobsCount(): Int = {
    jobs.count(x => x.isRunning())
  }

  // Thin wrapper around logger so that logging is centralized to JobManager, but this can be used outside
  def logInfo(message: String): Unit = {
    logger.info(message)
  }

  def logError(message: String): Unit = {
    logger.error(message)
  }

  /*
    General job query questions that may be reused
  */
  private def allJobsComplete(): Boolean = {
    getIncompleteJobs().isEmpty
  }

  private def getIncompleteJobs(): List[Job] = {
    jobs.filter(x => x.isRunning()).toList
  }

  private def mapStatus(): Map[String, Int] = {
    jobs.map(x => x.getJobStatus).groupBy(identity).mapValues(_.size)
  }
}
