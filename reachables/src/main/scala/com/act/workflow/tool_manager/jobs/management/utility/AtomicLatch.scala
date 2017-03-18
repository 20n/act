/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.workflow.tool_manager.jobs.management.utility

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
