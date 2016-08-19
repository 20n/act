package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.tool_wrappers.ScalaJobWrapper
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class JobsTest extends FlatSpec with Matchers with BeforeAndAfterEach with TimeLimitedTests {
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 1 second

  override def beforeEach(): Unit = {
    JobManager.setVerbosity(0)
  }

  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  /**
    * Constructs an instantly evaluating job that runs really fast
    *
    * @param name Name of the job
    *
    * @return newly constructed job.
    */
  def immediateReturnJob(name: String): ScalaJob = {
    val excitingFunction: () => Unit = () => Unit
    ScalaJobWrapper.wrapScalaFunction(name, excitingFunction)
  }


  /**
    * Sleeps the thread for a given amount of time to simulate a long-running job
    *
    * @param name Name of the job
    * @param time Time it runs for
    *
    * @return A nicely, newly constructed job
    */
  def longRunningJob(name: String, time: Long): ScalaJob = {
    val excitingFunction: () => Unit = () => Thread.sleep(time)
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

    A.internalState.statusManager.isCompleted should be(true)
    B.internalState.statusManager.isCompleted should be(true)
    C.internalState.statusManager.isCompleted should be(true)
    D.internalState.statusManager.isCompleted should be(true)

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
    val C = longRunningJob("C", 50000)

    A.thenRunBatch(List(B, C))
    B.thenRun(b1)

    JobManager.startJobAndKillWorkflowAfterSpecificJobCompletes(A, b1)

    // C should be killed as it completes after b3 based on time.
    A.internalState.statusManager.isCompleted should be(true)
    B.internalState.statusManager.isCompleted should be(true)
    b1.internalState.statusManager.isCompleted should be(true)
    C.internalState.statusManager.isKilled should be(true)
    C.internalState.statusManager.isFailed should be(true)
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

    println(JobManager.getOrderOfJobCompletion)
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
    B.internalState.killIncompleteJobs()

    JobManager.startJobAndAwaitUntilWorkflowComplete(A)


    A.internalState.statusManager.isSuccessful should be(true)
    B.internalState.statusManager.isKilled should be(true)
    C.internalState.statusManager.isSuccessful should be(true)
    D.internalState.statusManager.isSuccessful should be(true)
    E.internalState.statusManager.isSuccessful should be(true)
    F.internalState.statusManager.isKilled should be(true)
    G.internalState.statusManager.isKilled should be(true)
  }
}
