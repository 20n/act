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

package com.act.biointerpretation.l2expansion.sparkprojectors.utility

import java.io.File

import chemaxon.license.LicenseManager
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.log4j.LogManager

trait ProjectorCliHelper {
  final val OPTION_HELP = "h"

  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE: String
  /**
    * A class full of a few command line helpers for SparkRoProjectors
    */
  private val LOGGER = LogManager.getLogger(getClass)
  HELP_FORMATTER.setWidth(100)

  final def checkLicenseFile(licenseFile: String): File = {
    LOGGER.info(s"Validating license file at $licenseFile")
    LicenseManager.setLicenseFile(licenseFile)
    new File(licenseFile)
  }

  final def parseCommandLine(args: Array[String]): CommandLine = {
    val opts: Options = buildOptions(getCommandLineOptions)
    // Parse command line options
    val cl: Option[CommandLine] = {
      try {
        val parser = new DefaultParser()
        Option(parser.parse(opts, args))
      } catch {
        case e: ParseException =>
          LOGGER.error(s"Argument parsing failed: ${e.getMessage}\n")
          exitWithHelp(opts)
          None
      }
    }

    if (cl.isEmpty) {
      LOGGER.error("Detected that command line parser failed to be constructed.")
      exitWithHelp(opts)
    }

    if (cl.get.hasOption(OPTION_HELP)) exitWithHelp(opts)

    cl.get
  }

  private def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  private def buildOptions(opts: List[CliOption.Builder]): Options = {
    val unconstructedOptions = getCommandLineOptions

    // Build options
    val opts: Options = new Options()
    for (opt <- unconstructedOptions) {
      opts.addOption(opt.build)
    }

    opts
  }

  def getCommandLineOptions: List[CliOption.Builder]
}
