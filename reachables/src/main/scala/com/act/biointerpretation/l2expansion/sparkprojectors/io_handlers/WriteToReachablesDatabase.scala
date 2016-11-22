package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import act.installer.reachablesexplorer.{Loader, ReachablesProjectionUpdate}
import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait WriteToReachablesDatabase extends ReadFromDatabase with BasicProjectorOutput {
  final def handleOutput(cli: CommandLine)(results: Stream[ProjectionResult]) = {
    val loader = new Loader(getReadDbName(cli), getReadDbPort(cli), getReadDbHost(cli), getReadDbCollection(cli))
    writeToReachablesDatabaseThroughLoader(results, loader)
  }

  private def writeToReachablesDatabaseThroughLoader(results: Stream[ProjectionResult], loader: Loader): Unit = {
    results.foreach(projection => {
      println(projection)
      val updater: ReachablesProjectionUpdate = new ReachablesProjectionUpdate(projection)
      updater.updateByLoader(loader)
    })
  }

  def getTerminationCommandLineOptions: List[CliOption.Builder] = {
    List()
  }
}
