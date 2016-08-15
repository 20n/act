package com.act.workflow.tool_manager.jobs.management

class JobStatus {
  private var status = StatusCodes.Unstarted

  def isCompleted: Boolean = synchronized {
    isSuccessful | isFailed | isKilled
  }

  def isKilled: Boolean = synchronized {
    getJobStatus == StatusCodes.Killed
  }

  def isSuccessful: Boolean = synchronized {
    getJobStatus == StatusCodes.Success
  }

  def isFailed: Boolean = synchronized {
    val currentJobStatus = getJobStatus
    currentJobStatus == StatusCodes.Failure |
      currentJobStatus == StatusCodes.ParentProcessFailure |
      currentJobStatus == StatusCodes.Killed
  }

  def isUnstarted: Boolean = synchronized {
    getJobStatus == StatusCodes.Unstarted
  }

  def isRunning: Boolean = synchronized {
    getJobStatus == StatusCodes.Running
  }

  def getJobStatus: String = synchronized {
    this.status
  }

  def setJobStatus(newStatus: String): Unit = {
    /*
        We need to synchronize this so that status updates don't start hitting race conditions.
        We only synchronize over the status update because otherwise
        we have locking problems because isCompleted is also synchronized.
      */
    this.synchronized {
      status = newStatus
    }
  }

  override def toString: String = {
    status
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

}
