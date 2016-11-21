package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait BasicOutput {
  def handleOutput(cli: CommandLine)(results: Stream[ProjectionResult])

  def getTerminationCommandLineOptions: List[CliOption.Builder]
}
