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

import act.installer.reachablesexplorer.{Loader, ReachablesProjectionUpdate}
import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait WriteToReachablesDatabase extends ReadFromDatabase with BasicProjectorOutput {
  final def handleOutput(cli: CommandLine)(results: Stream[ProjectionResult]) = {
    val loader = new Loader(getReadDbHost(cli), getReadDbPort(cli), getReadDbName(cli), getReadDbCollection(cli))
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
    // This adds no new command line options, so we leave this as not final so that it can be overridden,
    // but have it return list as it does need to return a list type.
    List()
  }
}
