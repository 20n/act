package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}
import org.apache.commons.cli.HelpFormatter

trait Workflow {
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)

  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(): Job

  def parseArgs(args: List[String]) {}

  // This workflow will block all other execution until all queued jobs complete.
  def startWorkflowBlocking(): Unit = {
    startWorkflow()
    JobManager.awaitUntilAllJobsComplete()
  }

  // This allows you to get the job back so that you can choose if to block or not
  def startWorkflow(): Job = {
    val firstJob = createWorkflow()
    firstJob.start()
    firstJob
  }

  private def createWorkflow(): Job = {
    defineWorkflow()
  }

  def setArgument(argName: String, value: List[String]): Unit = {
  }
}

