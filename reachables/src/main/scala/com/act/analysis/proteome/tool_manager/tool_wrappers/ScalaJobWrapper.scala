package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.{JobManager, ScalaJob, ShellJob}

object ScalaJobWrapper {
  def wrapScalaFunction(f: () => Unit, retryJob: Boolean = false): ScalaJob ={
    val job = new ScalaJob(f)
    if (!retryJob)
      JobManager.addJob(job)

    job
  }
}
