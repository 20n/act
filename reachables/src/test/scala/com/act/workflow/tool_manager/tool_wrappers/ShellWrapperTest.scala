package com.act.workflow.tool_manager.tool_wrappers

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.jobs.management.JobManager
import org.scalatest._

class ShellWrapperTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def afterEach(): Unit = {
    JobManager.clearManager()
  }

  def successfulJob(command: Job): Unit = {
    command.isSuccessful should be(true)
    command.isFailed should be(false)
    command.isCompleted should be(true)
    command.isRunning should be(false)
    command.isUnstarted should be(false)
    command.returnCode should be(0)
  }

  "The ShellWrapper" should "not start jobs prior to start being called" in {
    val command = ShellWrapper.shellCommand("date", List("date"))

    command.isSuccessful should be(false)
    command.isFailed should be(false)
    command.isCompleted should be(false)
    command.isRunning should be(false)
    command.isUnstarted should be(true)
    command.returnCode should be(-1)
  }

  "The ShellWrapper" should "indicate valid commands complete" in {
    val command = ShellWrapper.shellCommand("date", List("date"))
    command.start()

    JobManager.awaitUntilAllJobsComplete()

    successfulJob(command)
  }


  "The ShellWrapper" should "report that commands that fail on shell fail" in {
    val command = ShellWrapper.shellCommand("cp", List("cp"))
    command.start()
    JobManager.awaitUntilAllJobsComplete()

    command.isSuccessful should be(false)
    command.isFailed should be(true)
    command.isCompleted should be(true)
    command.isRunning should be(false)
    command.isUnstarted should be(false)
    command.returnCode shouldNot be(0)
  }

  "The ShellWrapper" should "indicate a job is running when it is running" in {
    val command = ShellWrapper.shellCommand("sleep", List("sleep", "5"))
    command.start()

    command.isSuccessful should be(false)
    command.isFailed should be(false)
    command.isCompleted should be(false)
    command.isRunning should be(true)
    command.isUnstarted should be(false)
    command.returnCode shouldNot be(0)
  }

  "The ShellWrapper" should "allow for chaining of jobs" in {
    val command1 = ShellWrapper.shellCommand("date", List("date"))
    val command2 = ShellWrapper.shellCommand("ls", List("ls"))

    command1.thenRun(command2)
    command1.start()

    JobManager.awaitUntilAllJobsComplete()

    command1.isSuccessful should be(true)
    command1.isFailed should be(false)
    command1.isCompleted should be(true)
    command1.isRunning should be(false)
    command1.isUnstarted should be(false)
    command1.returnCode should be(0)

    command2.isSuccessful should be(true)
    command2.isFailed should be(false)
    command2.isCompleted should be(true)
    command2.isRunning should be(false)
    command2.isUnstarted should be(false)
    command2.returnCode should be(0)
  }
}