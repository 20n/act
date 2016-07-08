package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}

abstract class Workflow {
  private var head : Option[Job] = None

  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(): Job

  def createWorkflow(): Unit = {
    head = Option(defineWorkflow())
  }

  // This allows you to get the job back so that you can choose if to block or not
  def startWorkflow(): Option[Job] ={
    if (head.isEmpty)
      createWorkflow()
      JobManager.logInfo("Created jobs in workflow prior to starting")

    if (head.isDefined)
      head.get.start()
    else
      JobManager.logInfo("Workflow could not be instantiated due to no starting job"); None
  }

  // This workflow will block all other execution until all queued jobs complete.
  def startWorkflowBlocking(): Unit = {
    startWorkflow()
    JobManager.awaitUntilAllJobsComplete()
  }
}
