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

import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.{Level, LogManager}

object LoggingController {

  val verbosityMap = Map(
    0 -> Level.OFF,
    1 -> Level.FATAL,
    2 -> Level.ERROR,
    3 -> Level.INFO,
    4 -> Level.DEBUG,
    5 -> Level.TRACE,
    6 -> Level.ALL
  )

  def setVerbosity(level: Level): Unit = {
    val ctx: LoggerContext = LogManager.getContext(false).asInstanceOf[LoggerContext]
    val config = ctx.getConfiguration
    val loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)
    loggerConfig.setLevel(level)
    ctx.updateLoggers()
  }
}
