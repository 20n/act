package com.act.analysis.proteome.tool_manager.jobs

import scala.concurrent.{Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class ScalaJob(command: () => Unit) extends Job{
  def asyncJob() {
    // Run the call in the future
    val future: Future[Any] = Future {
      blocking {
        this.command()
      }
    }

    // Setup Job's success/failure
    future.onComplete({
      // Does not mean that the job succeeded, just that the future did
      case Success(x) => markAsSuccess()
      // This is a failure of the future to complete because of a JVM exception
      case Failure(x) => markAsFailure()
    })
  }
}
