package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.JobManager
import org.scalatest._

class ShellWrapperTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def beforeEach(): Unit = {
    JobManager.setVerbosity(0)
  }

  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  def successfulJob(command: Job): Unit = {
    command.internalState.status.isSuccessful should be(true)
    command.internalState.status.isFailed should be(false)
    command.internalState.status.isCompleted should be(true)
    command.internalState.status.isRunning should be(false)
    command.internalState.status.isUnstarted should be(false)
    command.internalState.returnCode should be(0)
  }

  "The ShellWrapper" should "not start jobs prior to start being called" in {
    val command = ShellWrapper.shellCommand("date", List("date"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    command.internalState.status.isSuccessful should be(false)
    command.internalState.status.isFailed should be(false)
    command.internalState.status.isCompleted should be(false)
    command.internalState.status.isRunning should be(false)
    command.internalState.status.isUnstarted should be(true)
    command.internalState.returnCode should be(-1)
  }

  "The ShellWrapper" should "indicate valid commands complete" in {
    val command = ShellWrapper.shellCommand("date", List("date"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    JobManager.startJobAndAwaitUntilWorkflowComplete(command)

    successfulJob(command)
  }


  "The ShellWrapper" should "report that commands that fail on shell fail" in {
    val command = ShellWrapper.shellCommand("cp", List("cp"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    JobManager.startJobAndAwaitUntilWorkflowComplete(command)

    command.internalState.status.isSuccessful should be(false)
    command.internalState.status.isFailed should be(true)
    command.internalState.status.isCompleted should be(true)
    command.internalState.status.isRunning should be(false)
    command.internalState.status.isUnstarted should be(false)
    command.internalState.returnCode shouldNot be(0)
  }

  "The ShellWrapper" should "indicate a job is running when it is running" in {
    val command = ShellWrapper.shellCommand("sleep", List("sleep", "5"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    command.start()

    command.internalState.status.isSuccessful should be(false)
    command.internalState.status.isFailed should be(false)
    command.internalState.status.isCompleted should be(false)
    command.internalState.status.isRunning should be(true)
    command.internalState.status.isUnstarted should be(false)
    command.internalState.returnCode shouldNot be(0)
  }

  "The ShellWrapper" should "allow for chaining of jobs" in {
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