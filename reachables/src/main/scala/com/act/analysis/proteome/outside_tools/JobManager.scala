package com.act.analysis.proteome.outside_tools

import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._

// TODO Handle the executionContext so that you can set max processes

/**
  * Manages all job processes and takes care of logging and blocking program exit
  */
object JobManager {
  // Futures are currently not looked into, but could be useful
  private val futures = new ListBuffer[Future[Any]]()
  // Can be used to ensure completion of all jobs w/ awaitUntilAllJobsComplete()
  private val jobs = new ListBuffer[Job]()
  // General logger which can be used outside of this class too
  private val logger = LogManager.getLogger(getClass.getName)

  def addFuture(jobFuture: Future[Any]) {
    futures.append(jobFuture)
  }

  def addJob(job: Job): Job = {
    jobs.append(job)
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
  def awaitUntilAllJobsComplete(sleepDuration: Duration = 10 seconds): Unit = {
    while (!allJobsComplete()) {
      logger.info(s"<Concurrent jobs running = ${runningJobsCount}>")
      logger.info(s"<Current jobs awaiting to run = ${waitingJobsCount}>")
      logger.info(s"<Completed jobs = ${completedJobsCount}>")
      getIncompleteJobs().map(x => logger.info(s"Running command is ${x.toString}"))
      Thread.sleep(sleepDuration.toMillis)
    }
    logger.info("All jobs have completed.")
    logger.info(s"Number of jobs run = ${completedJobsCount()}")
    logger.info(s"Number of jobs added but not run = ${unstartedJobsCount()}")
  }

  /*
    General job query questions that may be reused
  */
  private def allJobsComplete(): Boolean = {
    getIncompleteJobs().isEmpty
  }

  private def unstartedJobsCount(): Int = {
    jobs.count(x => x.isUnstarted())
  }

  private def getIncompleteJobs(): List[Job] = {
    jobs.filter(x => x.isRunning()).toList
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
}
