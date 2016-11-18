package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import act.installer.reachablesexplorer.{Loader, ReachablesProjectionUpdate}
import com.act.biointerpretation.l2expansion.sparkprojectors.ProjectionResult
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait WriteToReachablesDatabase extends ReadFromDatabase {
  final def handleTermination(cli: CommandLine)(results: Iterator[ProjectionResult]) = {
    val loader = new Loader(getDbName(cli), getDbPort(cli), getDbHost(cli), getDbCollection(cli))
    writeToReachablesDatabaseThroughLoader(results, loader)
  }

  private def writeToReachablesDatabaseThroughLoader(results: Iterator[ProjectionResult], loader: Loader): Unit = {
    results.foreach(projection => {
      val updater: ReachablesProjectionUpdate = new ReachablesProjectionUpdate(projection)
      updater.updateByLoader(loader)
    })
  }

  def getTerminationCommandLineOptions: List[CliOption.Builder] = {
    List()
  }
}
