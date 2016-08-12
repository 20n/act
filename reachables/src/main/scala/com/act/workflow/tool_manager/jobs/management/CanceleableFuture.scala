package com.act.workflow.tool_manager.jobs.management

import java.util.concurrent.CancellationException

import scala.concurrent.{ExecutionContext, Future, Promise}

object CanceleableFuture {
  def create[T](fun: Future[T] => T)(implicit ex: ExecutionContext): (Future[T], () => Boolean) = {
    val p = Promise[T]()
    val f = p.future
    p tryCompleteWith Future(fun(f))
    (f, () => p.tryFailure(new CancellationException))
  }
}
