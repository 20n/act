package com.act.workflow.tool_manager.jobs.management.utility

object JobFlag extends Enumeration {
  val ShouldNotBeWaitedOn, ShouldNotFailChildrenJobs = Value
}
