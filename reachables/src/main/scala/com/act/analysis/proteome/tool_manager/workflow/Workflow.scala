package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}

abstract class Workflow {
  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(): Job

  def parseArgs(args: List[String]) {
    checkIfAllFieldsAreSomething()
    startWorkflowBlocking()
  }

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

  def checkIfAllFieldsAreSomething(): Boolean = {
    // Setup reflection for this class instance
    val ru = scala.reflect.runtime.universe
    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val instanceMirror = mirror.reflect(this)

    // Iterate over all the fields in this class
    for (field <- this.getClass.getDeclaredFields.toList) {
      // Get the field and value of that field
      val term = ru.typeOf[this.type].declaration(ru.newTermName(field.getName)).asTerm.accessed.asTerm
      val fieldMirror = instanceMirror.reflectField(term)

      // Check if fields are something
      if (fieldMirror.get.asInstanceOf[Option[_]].isEmpty)
        return false
    }

    true
  }
}

