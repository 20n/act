package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.utility.{InternalState, JobFlag, StatusCodes, StatusManager}
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable.ListBuffer

/**
  * Job just defines the API
  *
  * To figure out what's up: https://github.com/20n/act/wiki/Scala-Workflows
  *
  * @param name - String name of the workflow
  */
abstract class Job(name: String) {
  val internalState = new InternalState(this)
  private val logger: Logger = LogManager.getLogger(getClass.getName)
  private val flags: ListBuffer[JobFlag.Value] = ListBuffer[JobFlag.Value]()

  def addFlag(value: JobFlag.Value): Unit = flags.append(value)
  def getFlags: List[JobFlag.Value] = flags.toList

  def retryJob: Option[Job] = _retryJob

  def retryJob_=(value: Option[Job]): Unit = _retryJob = value

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
