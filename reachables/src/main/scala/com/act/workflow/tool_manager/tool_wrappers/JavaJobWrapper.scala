package com.act.workflow.tool_manager.tool_wrappers

import com.act.jobs.JavaRunnable
import com.act.workflow.tool_manager.jobs.JavaJob
import com.act.workflow.tool_manager.jobs.management.JobManager

object JavaJobWrapper {
  def wrapJavaFunction(name: String, f: JavaRunnable,
                       retryJob: Boolean = false): JavaJob = {
    val job = new JavaJob(name, f)
    if (!retryJob)
      JobManager.addJob(job)
    job
  }
}
