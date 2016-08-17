package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.jobs.management.utility.JobFlag
import com.act.workflow.tool_manager.tool_wrappers.{ScalaJobWrapper, ShellWrapper}
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class JobsTest extends FlatSpec with Matchers with BeforeAndAfterEach with TimeLimitedTests {
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 200 millis

  override def beforeEach(): Unit = {
    JobManager.setVerbosity(0)
  }

  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  def immediateReturnJob(name: String): ScalaJob = {
    val excitingFunction: () => Unit = () => Unit
    ScalaJobWrapper.wrapScalaFunction(name, excitingFunction)
  }

  "Jobs" should "only be able to be run once." in {
    /*
      Structure of this test:
      #  A -> B -> C -> D

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    val C = immediateReturnJob("C")
    val D = immediateReturnJob("D")

    A.thenRun(B).thenRun(C).thenRun(D)
    JobManager.startJobAndAwaitUntilWorkflowComplete(A)

    A.getInternalState.status.isCompleted should be(true)
    B.getInternalState.status.isCompleted should be(true)
    C.getInternalState.status.isCompleted should be(true)
    D.getInternalState.status.isCompleted should be(true)

    JobManager.getOrderOfJobCompletion should be(List("A", "B", "C", "D"))
  }

  "Jobs" should "should complete parallel paths independently of how long each take." in {
    /*

      C runs much longer than B + b1 + b2 + b3, so we expect C to complete last even though it was started with B.
      We don't actually want our tests to wait for C,
      so we just verify that everything but C completes in the correct order.

      Structure of this test:
      #     B -> b1 -> b2 -> b3
      #   /
      # A
      #  \
      #    -------> C

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    val b1 = immediateReturnJob("b1")
    // Won't take 50 seconds as should exit after b3 complete.
    val C = ShellWrapper.shellCommand("C", List("sleep", "50"))

    A.thenRunBatch(List(B, C))
    B.thenRun(b1)

    JobManager.startJobAndKillWorkflowAfterSpecificJobCompletes(A, b1)

    // C should be killed as it completes after b3 based on time.
    A.getInternalState.status.isCompleted should be(true)
    B.getInternalState.status.isCompleted should be(true)
    b1.getInternalState.status.isCompleted should be(true)
    C.getInternalState.status.isKilled should be(true)
    C.getInternalState.status.isFailed should be(true)
  }

  "Jobs" should "be able to have two divergent branches come together at the end" in {
    /*
      Structure of this test:
      #     B -> b1 -> b2 -> b3
      #   /                    \
      # A                       D
      #  \                     /
      #    -------> C --------

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    val b1 = immediateReturnJob("b1")
    val b2 = immediateReturnJob("b2")
    val b3 = immediateReturnJob("b3")
    val C = immediateReturnJob("C")
    val D = immediateReturnJob("D")

    A.thenRunBatch(List(B, C)).thenRun(D)
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    JobManager.startJobAndAwaitUntilWorkflowComplete(A)

    // Last job should be D
    JobManager.getOrderOfJobCompletion.last should be("D")
  }

  "Jobs" should "be able to have a convergent branch start more divergence" in {
    /*
      Structure of this test:
      #     B ->         E
      #   /       \    /
      # A          D -
      #  \        /    \
      #     C ->         F

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    val C = immediateReturnJob("C")
    val D = immediateReturnJob("D")
    val E = immediateReturnJob("E")
    val F = immediateReturnJob("F")

    A.thenRunBatch(List(B, C)).thenRun(D).thenRunBatch(List(E, F))

    JobManager.startJobAndAwaitUntilWorkflowComplete(A)

    // B and C and E and F are in parallel so order can vary
    JobManager.getOrderOfJobCompletion.head should be("A")
    JobManager.getOrderOfJobCompletion(2) should (equal("B") or equal("C"))
    JobManager.getOrderOfJobCompletion(3) should be("D")
    JobManager.getOrderOfJobCompletion(4) should (equal("E") or equal("F"))
  }

  "Jobs" should "be able to create independent jobs by indicating a given job shouldn't be waited for" in {
    /*
      Structure of this test:
      #     B -> F -> G
      #   /
      # A - D ->  E
      #  \       /
      #     C ->

     */
    val A = immediateReturnJob("A")
    val B = immediateReturnJob("B")
    B.addFlag(JobFlag.ShouldNotBeWaitedOn)

    val C = immediateReturnJob("C")
    val D = immediateReturnJob("D")
    val E = immediateReturnJob("E")
    val F = immediateReturnJob("F")
    val G = immediateReturnJob("G")

    A.thenRunBatch(List(B, C, D)).thenRun(E)
    B.thenRun(G).thenRun(F)

    // If we kill B, E should still be allowed to complete.
    B.getInternalState.killIncompleteJobs()

    JobManager.startJobAndAwaitUntilWorkflowComplete(A)


    A.getInternalState.status.isSuccessful should be(true)
    B.getInternalState.status.isKilled should be(true)
    C.getInternalState.status.isSuccessful should be(true)
    D.getInternalState.status.isSuccessful should be(true)
    E.getInternalState.status.isSuccessful should be(true)
    F.getInternalState.status.isKilled should be(true)
    G.getInternalState.status.isKilled should be(true)
  }
}
