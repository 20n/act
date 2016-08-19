package com.act.workflow.tool_manager.jobs.management.utility

import java.util.concurrent.CancellationException

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * This future returns two things, a future and a function.
  * If the function is executed, it will cancel the future.  Otherwise, nothing will happen.
  *
  * Example:
  * (f, cancel) = new CanceleableFuture(future)
  * cancel()
  */
object CanceleableFuture {
  def create[T](fun: Future[T] => T)(implicit ex: ExecutionContext): (Future[T], () => Boolean) = {
    val p = Promise[T]()
    val f = p.future
    p tryCompleteWith Future(fun(f))
    (f, () => p.tryFailure(new CancellationException))
  }
}
