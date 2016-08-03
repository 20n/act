package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.ScalaJob
import com.act.analysis.proteome.tool_manager.jobs.management.JobManager

object ScalaJobWrapper {
  def wrapScalaFunction(f: () => Unit, retryJob: Boolean = false): ScalaJob = {
    val job = new ScalaJob(f)
    if (!retryJob)
      JobManager.addJob(job)

    job
  }
}
