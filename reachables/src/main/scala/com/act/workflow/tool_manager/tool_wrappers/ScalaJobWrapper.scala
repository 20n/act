package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.ScalaJob
import com.act.workflow.tool_manager.jobs.management.JobManager

object ScalaJobWrapper {
  def wrapScalaFunction(f: () => Unit, retryJob: Boolean = false): ScalaJob = {
    val job = new ScalaJob(f)
    if (!retryJob)
      JobManager.addJob(job)

    job
  }
}
