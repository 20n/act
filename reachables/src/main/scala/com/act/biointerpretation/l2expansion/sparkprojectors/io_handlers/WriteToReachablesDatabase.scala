package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import act.installer.reachablesexplorer.{Loader, ReachablesProjectionUpdate}
import com.act.biointerpretation.l2expansion.sparkprojectors.ProjectionResult
import org.apache.commons.cli.CommandLine

trait WriteToReachablesDatabase extends ReadFromDatabase {
  final def handleTermination(cli: CommandLine)(results: Iterator[ProjectionResult]) = {
    writeToReachablesDatabaseThroughLoader(results)
  }

  private def writeToReachablesDatabaseThroughLoader(results: Iterator[ProjectionResult]): Unit = {
    // TODO Add args to this
    val loader = new Loader()

    results.foreach(projection => {
      val updater: ReachablesProjectionUpdate = new ReachablesProjectionUpdate(projection)
      updater.updateByLoader(loader)
    })
  }
}
