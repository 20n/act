package com.act.workflow.tool_manager.jobs.management

import com.act.workflow.tool_manager.jobs.ScalaJob
import com.act.workflow.tool_manager.tool_wrappers.ScalaJobWrapper
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class JobManagerTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  def immediateReturnJob(name: String): ScalaJob = {
    val excitingFunction: () => Unit = () => Unit
    ScalaJobWrapper.wrapScalaFunction(name, excitingFunction)
  }

  // Job complete size should be the same as number of jobs successful.
  "Job Manager" should "have an equal count of completed jobs and " +
    "number of jobs in list of order in which jobs completed." in {
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("A")
    val b1 = immediateReturnJob("b1")
    val b2 = immediateReturnJob("b2")
    val b3 = immediateReturnJob("b3")
    val C = immediateReturnJob("C")
    val D = immediateReturnJob("D")

    A.thenRunBatch(List(B, C)).thenRun(D)
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    JobManager.awaitUntilAllJobsComplete(A)

    JobManager.getOrderOfJobCompletion.length should be(JobManager.completedJobsCount())

    // If the world starts falling.
    JobManager.getOrderOfJobCompletion.length should be(7)
    JobManager.completedJobsCount() should be(7)
  }

  // If we ask the job manager to wait until one job is done, it should kill any jobs still in the queue after that.
  "Job Manager" should "should cancel any incomplete job after the job we are waiting for is complete." in {
    /*
      Structure of this test:
      #
      # A -> B -> C
      #       \
      #        -------> b1 -> b2 -> b3

      But we kill it at Job B so C, b1, b2, and b3 should not complete.

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    val b1 = immediateReturnJob("b1")
    val b2 = immediateReturnJob("b2")
    val b3 = immediateReturnJob("b3")
    val C = immediateReturnJob("C")

    A.thenRun(B).thenRun(C)
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    JobManager.awaitUntilSpecificJobComplete(A, B)

    A.isCompleted should be(true)
    B.isCompleted should be(true)
    b1.isKilled should be(true)
    b2.isKilled should be(true)
    b3.isKilled should be(true)
    C.isKilled should be(true)
  }

  "Jobs" should "detect if not all jobs have been added to run." in {
    /*
      Structure of this test:
      #
      # A          B
      #

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")

    an[RuntimeException] should be thrownBy JobManager.awaitUntilAllJobsComplete(A)
  }
}
