package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.{JobManager, ScalaJob}

object ScalaJobWrapper {
  def wrapScalaFunction(f: Map[String, Any] => Unit, arguments: Map[String, Any], retryJob: Boolean = false): ScalaJob = {
    val job = new ScalaJob(f, arguments)
    if (!retryJob)
      JobManager.addJob(job)

    job
  }
}
