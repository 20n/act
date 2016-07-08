package com.act.analysis.proteome.tool_manager.workflow

import java.lang.UnsupportedOperationException

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}

abstract class Workflow {
  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(): Job

  private def createWorkflow(): Job = {
    defineWorkflow()
  }

  // This allows you to get the job back so that you can choose if to block or not
  def startWorkflow(): Job ={
    val firstJob = createWorkflow()
    firstJob.start()
    firstJob
  }

  // This workflow will block all other execution until all queued jobs complete.
  def startWorkflowBlocking(): Unit = {
    startWorkflow()
    JobManager.awaitUntilAllJobsComplete()
  }
}
