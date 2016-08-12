package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.tool_wrappers.ShellWrapper
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class JobsTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  "Jobs" should "run sequentially in order." in {
    /*
      Structure of this test:
      #  A -> B -> C -> D

     */
    val A = ShellWrapper.shellCommand("A", List("date"))
    val B = ShellWrapper.shellCommand("B", List("date"))
    val C = ShellWrapper.shellCommand("C", List("date"))
    val D = ShellWrapper.shellCommand("D", List("date"))

    A.thenRun(B).thenRun(C).thenRun(D)

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    // B and C can be ordered in either way because of running in paralle.
    val validOrders = List(List("A", "B", "C", "D"))

    validOrders.contains(JobManager.getOrderOfJobCompletion) should be(true)
  }

  "Jobs" should "run in parallel should keep order between children" in {
    /*

      B takes about as long as C, so they could complete in either order.

      Structure of this test:
      #     B -> b1 -> b2 -> b3
      #   /
      # A
      #  \
      #    -------> C

     */
    val A = ShellWrapper.shellCommand("A", List("sleep", "1"))
    val B = ShellWrapper.shellCommand("B", List("sleep", "1"))
    val b1 = ShellWrapper.shellCommand("b1", List("sleep", "1"))
    val b2 = ShellWrapper.shellCommand("b2", List("sleep", "1"))
    val b3 = ShellWrapper.shellCommand("b3", List("sleep", "1"))
    val C = ShellWrapper.shellCommand("C", List("sleep", "1"))

    A.thenRunBatch(List(B, C))
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    // B and C can be ordered in either way because of running in paralle.
    val validOrders = List(List("A", "B", "C", "b1", "b2", "b3"), List("A", "C", "B", "b1", "b2", "b3"))

    validOrders.contains(JobManager.getOrderOfJobCompletion) should be(true)
  }

  "Jobs" should "should complete parallel paths independently of how long each take." in {
    /*

      C runs much longer than B + b1 + b2 + b3, so we expect C to complete last even though it was started with B

      Structure of this test:
      #     B -> b1 -> b2 -> b3
      #   /
      # A
      #  \
      #    -------> C

     */
    val A = ShellWrapper.shellCommand("A", List("sleep", "1"))
    val B = ShellWrapper.shellCommand("B", List("sleep", "1"))
    val b1 = ShellWrapper.shellCommand("b1", List("sleep", "1"))
    val b2 = ShellWrapper.shellCommand("b2", List("sleep", "1"))
    val b3 = ShellWrapper.shellCommand("b3", List("sleep", "1"))
    val C = ShellWrapper.shellCommand("C", List("sleep", "6"))

    A.thenRunBatch(List(B, C))
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    // B and C can be ordered in either way because of running in paralle.
    val validOrders = List(List("A", "B", "b1", "b2", "b3", "C"))

    validOrders.contains(JobManager.getOrderOfJobCompletion) should be(true)
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
    val A = ShellWrapper.shellCommand("A", List("sleep", "1"))
    val B = ShellWrapper.shellCommand("B", List("sleep", "1"))
    val b1 = ShellWrapper.shellCommand("b1", List("sleep", "1"))
    val b2 = ShellWrapper.shellCommand("b2", List("sleep", "1"))
    val b3 = ShellWrapper.shellCommand("b3", List("sleep", "1"))
    val C = ShellWrapper.shellCommand("C", List("sleep", "1"))
    val D = ShellWrapper.shellCommand("D", List("sleep", "1"))

    A.thenRunBatch(List(B, C)).thenRun(D)
    B.thenRun(b1).thenRun(b2).thenRun(b3)

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    // B and C can be ordered in either way because of running in parallel.
    val validOrders = List(List("A", "B", "C", "b1", "b2", "b3", "D"), List("A", "C", "B", "b1", "b2", "b3", "D"))
    println(JobManager.getOrderOfJobCompletion)
    validOrders.contains(JobManager.getOrderOfJobCompletion) should be(true)
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
    val A = ShellWrapper.shellCommand("A", List("sleep", "1"))
    val B = ShellWrapper.shellCommand("B", List("sleep", "1"))
    val C = ShellWrapper.shellCommand("C", List("sleep", "1"))
    val D = ShellWrapper.shellCommand("D", List("sleep", "1"))
    val E = ShellWrapper.shellCommand("E", List("sleep", "1"))
    val F = ShellWrapper.shellCommand("F", List("sleep", "1"))

    A.thenRunBatch(List(B, C)).thenRun(D).thenRunBatch(List(E, F))

    A.start()
    JobManager.awaitUntilAllJobsComplete()

    // B and C and E and F are in parallel so order can vary
    val validOrders = List(
      List("A", "B", "C", "D", "E", "F"),
      List("A", "B", "C", "D", "F", "E"),
      List("A", "C", "B", "D", "E", "F"),
      List("A", "C", "B", "D", "F", "E")
    )
    validOrders.contains(JobManager.getOrderOfJobCompletion) should be(true)
  }
}
