package com.act.analysis.proteome.tool_manager.jobs

import scala.concurrent.{Future, blocking}
import scala.sys.process.Process
import scala.util.{Failure, Success}

class HeaderJob extends Job {
  def asyncJob() {
    markAsSuccess()
  }
}
