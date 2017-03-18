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

package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.{BufferedWriter, File, FileWriter}

import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult
import org.apache.commons.cli.{CommandLine, Option => CliOption}
import spray.json.{DefaultJsonProtocol, _}

// Defines how we convert a Projection Result into a JSON document
object ProjectionResultProtocol extends DefaultJsonProtocol {
  implicit val projectionFormat = jsonFormat3(ProjectionResult)
}

import com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers.ProjectionResultProtocol._

trait WriteToJson extends BasicFileProjectorOutput {
  val OPTION_OUTPUT_DIRECTORY: String

  final def getTerminationCommandLineOptions: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_OUTPUT_DIRECTORY).
        required(true).
        hasArg.
        longOpt("output-directory").
        desc("A directory in which to write per-RO result files")
    )

    options
  }

  final def handleOutput(cli: CommandLine)(results: Stream[ProjectionResult]) = {
    val outputDir = getOutputDirectory(cli)
    createOutputDirectory(outputDir)
    val projectedReactionsFile = new File(outputDir, "projectedReactions")
    val buffer = new BufferedWriter(new FileWriter(projectedReactionsFile))

    // TODO Consider if we want to try using jackson/spray's streaming API?
    // Start array and write
    buffer.write("[")

    buffer.write(s"${results.head.toJson.prettyPrint}")

    // For each element in the iterator, write as a new element
    // TODO Consider buffer flushing after each write?
    results.tail.foreach(result => {
      buffer.write(s",${result.toJson.prettyPrint}")
    })

    // Close up the array and close the file.
    buffer.write("]")
    buffer.close()
  }

  private def getOutputDirectory(cli: CommandLine): File = {
    new File(cli.getOptionValue(OPTION_OUTPUT_DIRECTORY))
  }
}
