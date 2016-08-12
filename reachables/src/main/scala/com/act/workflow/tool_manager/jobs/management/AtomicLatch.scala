package com.act.workflow.tool_manager.jobs.management

import java.util.concurrent.atomic.AtomicInteger

class AtomicLatch {
  private val counter: AtomicInteger = new AtomicInteger(0)

  /*
  We synchronize all the methods on this object because we will enter IllegalStates and crash if we do not.
  It won't allow us to sync onto the AtomicInteger, so we just sync onto the AtomicLock instance.

  We only notify if the value ends up less than 0 from a set,
  or if we decrement because that is where we may enter a 0 or less state.
   */


  /**
    * Increases the counter by one.
    */
  def countUp(): Unit = {
    this.synchronized {
      counter.incrementAndGet
    }
  }

  /**
    * Decrements the counter
    */
  def countDown(): Unit = {
    this.synchronized {
      counter.decrementAndGet()
      this.notify()
    }
  }

  def releaseLock(): Unit = {
    this.synchronized {
      counter.set(0)
      this.notify()
    }
  }

  /**
    * Waits until conditions are met to release counter
    */
  def await() {
    this.synchronized {
      while (counter.get > 0) {
        this.wait()
      }
    }
  }

  /**
    * Returns the current value of the lock
    *
    * @return
    */
  def getCount: Integer = {
    this.synchronized {
      counter.get()
    }
  }

  /**
    * Sets the current value of the lock to something else.
    *
    * @param value The value to set the count to, as an integer
    */
  def setCount(value: Integer): Unit = {
    this.synchronized {
      val newValue = counter.getAndSet(value)
      if (newValue <= 0) {
        notify()
      }
    }
  }
}
