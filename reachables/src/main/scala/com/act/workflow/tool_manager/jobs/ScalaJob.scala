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

package com.act.workflow.tool_manager.jobs

import com.act.workflow.tool_manager.jobs.management.utility.CanceleableFuture
import org.apache.logging.log4j.LogManager

import scala.concurrent.CancellationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class ScalaJob(name: String, command: () => Unit) extends Job(name) {
  private val logger = LogManager.getLogger(getClass.getName)

  def asyncJob() {
    // Run the call in the future
    val (future, cancel) = CanceleableFuture.create[Unit](future => {
      this.command()
    })
    addCancelFunction(cancel)

    // Setup Job's success/failure
    future.onComplete({
      case Success(x) => markAsSuccess()
      case Failure(x) =>
        if (x.isInstanceOf[CancellationException]) {
          logger.error("Future was canceled.")
        } else {
          markAsFailure()
          logger.error(s"Cause of failure was ${x.getMessage}.", x)
        }
    })
  }
}