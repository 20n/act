package com.act.analysis.proteome.outside_tools

object ShellWrapper extends ToolWrapper{
  def shellCommand(command: List[String], retryJob: Boolean = false): Job = {
    constructJob(toolFunction = "", command, retryJob)
  }
}
