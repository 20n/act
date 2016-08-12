package com.act.workflow.tool_manager.jobs

import com.act.jobs.JavaRunnable
import com.act.workflow.tool_manager.jobs.management.CanceleableFuture
import org.apache.logging.log4j.LogManager

import scala.concurrent.CancellationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class JavaJob(name: String, runnable: JavaRunnable) extends Job(name) {

  private val logger = LogManager.getLogger(getClass.getName)

  def asyncJob() {
    // Run the call in the future
    val (future, cancel) = CanceleableFuture.create[Any](future => {
      this.runnable.run()
    })
    this.cancelFuture = Option(cancel)

    // Setup Job's success/failure
    future.onComplete({
      case Success(x) => markAsSuccess()
      case Failure(x) =>
        if (x.isInstanceOf[CancellationException]) {
          logger.error("Future was canceled.")
        } else {
          markAsFailure()
          logger.error(s"Cause of failure was ${x.getMessage}.")
          x.printStackTrace()
        }
    })
  }

  override def toString(): String = {
    runnable.toString()
  }
}
