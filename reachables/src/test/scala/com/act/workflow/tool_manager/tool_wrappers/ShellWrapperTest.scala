package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.JobManager
import org.apache.commons.lang.SystemUtils.IS_OS_UNIX
import org.scalatest._
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._

class ShellWrapperTest extends FlatSpec with Matchers with TimeLimitedTests with BeforeAndAfterEach {
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 200 millis

  override def beforeEach(): Unit = {
    JobManager.setVerbosity(0)
  }

  override def afterEach(): Unit = {
    JobManager.clearManager()
  }


  def successfulJob(command: Job): Unit = {
    command.internalState.statusManager.isSuccessful should be(true)
    command.internalState.statusManager.isFailed should be(false)
    command.internalState.statusManager.isCompleted should be(true)
    command.internalState.statusManager.isRunning should be(false)
    command.internalState.statusManager.isNotStarted should be(false)
    command.internalState.getReturnCode should be(0)
  }

  "The ShellWrapper" should "not start jobs prior to start being called" in {
    if (IS_OS_UNIX) {
      val command = ShellWrapper.shellCommand("date", List("date"))
      command.doNotWriteOutputStream()
      command.doNotWriteErrorStream()

      command.internalState.statusManager.isSuccessful should be(false)
      command.internalState.statusManager.isFailed should be(false)
      command.internalState.statusManager.isCompleted should be(false)
      command.internalState.statusManager.isRunning should be(false)
      command.internalState.statusManager.isNotStarted should be(true)
      command.internalState.getReturnCode should be(-1)
    }
  }

  "The ShellWrapper" should "indicate valid commands complete" in {
    if (IS_OS_UNIX) {
      val command = ShellWrapper.shellCommand("date", List("date"))
      command.doNotWriteOutputStream()
      command.doNotWriteErrorStream()

      JobManager.startJobAndAwaitUntilWorkflowComplete(command)

      successfulJob(command)
    }
  }


  "The ShellWrapper" should "report that commands that fail on shell fail" in {
    if (IS_OS_UNIX) {
      val command = ShellWrapper.shellCommand("cp", List("cp"))
      command.doNotWriteOutputStream()
      command.doNotWriteErrorStream()

      JobManager.startJobAndAwaitUntilWorkflowComplete(command)

      command.internalState.statusManager.isSuccessful should be(false)
      command.internalState.statusManager.isFailed should be(true)
      command.internalState.statusManager.isCompleted should be(true)
      command.internalState.statusManager.isRunning should be(false)
      command.internalState.statusManager.isNotStarted should be(false)
      command.internalState.getReturnCode shouldNot be(0)
    }
  }

  "The ShellWrapper" should "indicate a job is running when it is running" in {
    if (IS_OS_UNIX) {
      val command = ShellWrapper.shellCommand("sleep", List("sleep", "5"))
      command.doNotWriteOutputStream()
      command.doNotWriteErrorStream()

      command.start()

      command.internalState.statusManager.isSuccessful should be(false)
      command.internalState.statusManager.isFailed should be(false)
      command.internalState.statusManager.isCompleted should be(false)
      command.internalState.statusManager.isRunning should be(true)
      command.internalState.statusManager.isNotStarted should be(false)
      command.internalState.getReturnCode shouldNot be(0)
    }
  }

  "The ShellWrapper" should "allow for chaining of jobs" in {
    if (IS_OS_UNIX) {
      val command1 = ShellWrapper.shellCommand("date", List("date"))
      val command2 = ShellWrapper.shellCommand("ls", List("ls"))

      command1.doNotWriteOutputStream()
      command1.doNotWriteErrorStream()

      command2.doNotWriteOutputStream()
      command2.doNotWriteErrorStream()

      command1.thenRun(command2)

      JobManager.startJobAndAwaitUntilWorkflowComplete(command1)

      successfulJob(command1)
      successfulJob(command2)
    }
  }

}