package com.act.workflow.tool_manager.jobs.management.utility

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

final class JobStatus {
  private var status = StatusCodes.Unstarted

  def isCompleted: Boolean = {
    isSuccessful | isFailed | isKilled
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

  def isUnstarted: Boolean = {
    getJobStatus == StatusCodes.Unstarted
  }

  def isRunning: Boolean = {
    getJobStatus == StatusCodes.Running
  }

  override def toString: String = {
    getJobStatus
  }
}
