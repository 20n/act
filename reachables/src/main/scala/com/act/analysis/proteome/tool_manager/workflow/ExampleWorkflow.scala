package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.ShellWrapper


class ExampleWorkflow extends Workflow {
  def defineWorkflow(): Job = {
    // Print working directory
    val job1 = ShellWrapper.shellCommand(List("pwd"))
    job1.writeOutputStreamToLogger()

    // See which files are available
    val job2 = ShellWrapper.shellCommand(List("ls"))
    job2.writeOutputStreamToLogger()

    // Get today's date
    val job3 = ShellWrapper.shellCommand(List("date"))
    job3.writeOutputStreamToLogger()

    // Print date as just the hour
    val job4 = ShellWrapper.shellCommand(List("date", "+%H"))
    job4.writeOutputStreamToLogger()

    // Check directory again
    val job5 = ShellWrapper.shellCommand(List("pwd"))
    job5.writeOutputStreamToLogger()

    // Returns first job
    job1.thenRunBatch(List(job2, job3)).thenRun(job4).thenRun(job5)
  }
}
