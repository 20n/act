package com.act.workflow.tool_manager.jobs.management

import com.act.workflow.tool_manager.tool_wrappers.ShellWrapper
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class JobManagerTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  // Job complete size should be the same as number of jobs successful.
  "Job Manager" should "have an equal count of completed jobs and " +
    "number of jobs in list of order in which jobs completed." in {
    val A = ShellWrapper.shellCommand("A", List("date"))
    val B = ShellWrapper.shellCommand("B", List("date"))
    val b1 = ShellWrapper.shellCommand("b1", List("date"))
    val b2 = ShellWrapper.shellCommand("b2", List("date"))
    val b3 = ShellWrapper.shellCommand("b3", List("date"))
    val C = ShellWrapper.shellCommand("C", List("date"))
    val D = ShellWrapper.shellCommand("D", List("date"))

    A.thenRunBatch(List(B, C)).thenRun(D)
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    JobManager.getOrderOfJobCompletion.length should be(JobManager.completedJobsCount())
  }
}
