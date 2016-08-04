package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.JavaJob
import com.act.workflow.tool_manager.jobs.management.JobManager

object JavaJobWrapper {
  def wrapJavaFunction(f: () => Unit, retryJob: Boolean = false): JavaJob = {
    val job = new JavaJob(f)
    if (!retryJob)
      JobManager.addJob(job)

    job
  }
}
