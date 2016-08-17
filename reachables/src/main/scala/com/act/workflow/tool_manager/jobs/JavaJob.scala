package com.act.workflow.tool_manager.jobs

import com.act.jobs.JavaRunnable
import org.apache.logging.log4j.LogManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success}

class JavaJob(runnable: JavaRunnable) extends Job {

  private val logger = LogManager.getLogger(getClass.getName)

  def asyncJob() {
    // Run the call in the future
    val future: Future[Any] = Future {
      blocking {
        this.runnable.run()
      }
    }

    // Setup Job's success/failure
    future.onComplete({
      case Success(x) => markAsSuccess()
      case Failure(x) =>
        markAsFailure()
        logger.error(s"Cause of failure was ${x.getMessage}.")
        x.printStackTrace()
    })
  }

  override def toString(): String = {
    runnable.toString()
  }
}
