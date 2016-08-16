package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.{JobManager, LoggingVerbosity}
import org.scalatest._

class ShellWrapperTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def beforeEach(): Unit = {
    JobManager.setVerbosity(LoggingVerbosity.Off)
  }

  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  def successfulJob(command: Job): Unit = {
    command.getJobStatus.isSuccessful should be(true)
    command.getJobStatus.isFailed should be(false)
    command.getJobStatus.isCompleted should be(true)
    command.getJobStatus.isRunning should be(false)
    command.getJobStatus.isUnstarted should be(false)
    command.getReturnCode should be(0)
  }

  "The ShellWrapper" should "not start jobs prior to start being called" in {
    val command = ShellWrapper.shellCommand("date", List("date"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    command.getJobStatus.isSuccessful should be(false)
    command.getJobStatus.isFailed should be(false)
    command.getJobStatus.isCompleted should be(false)
    command.getJobStatus.isRunning should be(false)
    command.getJobStatus.isUnstarted should be(true)
    command.getReturnCode should be(-1)
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

    command.getJobStatus.isSuccessful should be(false)
    command.getJobStatus.isFailed should be(true)
    command.getJobStatus.isCompleted should be(true)
    command.getJobStatus.isRunning should be(false)
    command.getJobStatus.isUnstarted should be(false)
    command.getReturnCode shouldNot be(0)
  }

  "The ShellWrapper" should "indicate a job is running when it is running" in {
    val command = ShellWrapper.shellCommand("sleep", List("sleep", "5"))
    command.doNotWriteOutputStream()
    command.doNotWriteErrorStream()

    command.start()

    command.getJobStatus.isSuccessful should be(false)
    command.getJobStatus.isFailed should be(false)
    command.getJobStatus.isCompleted should be(false)
    command.getJobStatus.isRunning should be(true)
    command.getJobStatus.isUnstarted should be(false)
    command.getReturnCode shouldNot be(0)
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