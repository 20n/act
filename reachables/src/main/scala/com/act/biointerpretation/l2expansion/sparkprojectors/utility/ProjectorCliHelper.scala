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
