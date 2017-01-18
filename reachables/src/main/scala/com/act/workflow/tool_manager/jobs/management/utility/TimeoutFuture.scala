package com.act.workflow.tool_manager.jobs.management.utility

import java.util.concurrent.{TimeUnit, TimeoutException}

import io.netty.util.{HashedWheelTimer, Timeout, TimerTask}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}

// Stolen from http://stackoverflow.com/a/16305056/4978569
object TimeoutFuture {
  def create[T](fut:Future[T])(after: Duration): Future[T] = {
    val prom = Promise[T]()
    val timeout = TimeoutScheduler.scheduleTimeout(prom, after)
    val combinedFut = Future.firstCompletedOf(List(fut, prom.future))
    fut onComplete{case result => timeout.cancel()}
    combinedFut
  }
}

object TimeoutScheduler {
  val timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS)
  def scheduleTimeout(promise:Promise[_], after:Duration) = {
    timer.newTimeout(new TimerTask{
      def run(timeout:Timeout){
        promise.failure(new TimeoutException("Operation timed out after " + after.toMillis + " millis"))
      }
    }, after.toNanos, TimeUnit.NANOSECONDS)
  }
}
