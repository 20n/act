package com.act.workflow.tool_manager.jobs

/**
  * A header job is a dummy job that will run initially.
  * It can be useful if you are hoping to run a workflow that does multiple things in parallel,
  * then wants to come together at the end.
  *
  * You can then define a workflow as <Header> -> <Multiple Jobs> -> <Summary Jobs>
  */
class HeaderJob extends Job {
  def asyncJob(): Unit = {
    runNextJob()
  }
}
