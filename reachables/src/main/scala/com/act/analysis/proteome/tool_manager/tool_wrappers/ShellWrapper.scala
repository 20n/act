package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.Job

object ShellWrapper extends ToolWrapper{
  def shellCommand(command: List[String], retryJob: Boolean = false): Job = {
    require(command.nonEmpty, "A command must be passed in to execute")
    require(command.head != "cd", "Move directory commands are not allowed as this job manager does not hold directory state")
    constructJob(toolFunction = "", command, retryJob)
  }
}
