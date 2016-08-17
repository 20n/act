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
