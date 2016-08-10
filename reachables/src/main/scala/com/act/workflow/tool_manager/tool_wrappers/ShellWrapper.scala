package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.ShellJob

object ShellWrapper extends ToolWrapper{
  def shellCommand(command: List[String], retryJob: Boolean = false): ShellJob = {
    require(command.nonEmpty, "A command must be passed in to execute")
    require(command.head != "cd", "Move directory commands are not allowed as this job manager does not hold directory state")
    constructJob(toolFunction = None, args = command, retryJob = retryJob)
  }
}
