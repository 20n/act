package com.act.workflow.tool_manager.workflow

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.ShellWrapper
import org.apache.commons.cli.CommandLine


class ExampleWorkflow extends Workflow {
  def defineWorkflow(commandLine: CommandLine): Job = {
    // Print working directory
    val job1 = ShellWrapper.shellCommand(List("pwd"))

    // See which files are available
    val job2 = ShellWrapper.shellCommand(List("ls"))

    // Get today's date
    val job3 = ShellWrapper.shellCommand(List("date"))

    // Print date as just the hour
    val job4 = ShellWrapper.shellCommand(List("date", "+%H"))

    // Check directory again
    val job5 = ShellWrapper.shellCommand(List("pwd"))

    // Returns first job
    job1.thenRunBatch(List(job2, job3)).thenRun(job4).thenRun(job5)
  }
}
