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

package com.act.biointerpretation.networkanalysis

import java.io.File
import java.util.Optional

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._

/**
  * Read in a metabolism network from file, and do precursor calculations over it with respect to a set
  * of target inchis.
  */
class PrecursorAnalysisFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE =
    """Workflow for precursor analyses. Takes in a set of target molecules, as inchis, and
      |a metabolism network, and finds the precursors of the given molecules in the network. Writes out
      |one report per target molecule supplied.""".stripMargin

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_INPUT_NETWORK = "i"
  private val OPTION_INPUT_LCMS = "l"
  private val OPTION_TARGET_INCHIS = "t"
  private val OPTION_NUM_STEPS = "n"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc(
          """The directory in which to run and create all intermediate files.This directory will be created if it
            | does not already exist.""".stripMargin).
        required,

      CliOption.builder(OPTION_INPUT_NETWORK).
        hasArg.
        longOpt("input-network").
        desc("The file path to the input network.").
        required,

      CliOption.builder(OPTION_INPUT_LCMS).
        hasArg.
        longOpt("input-lcms").
        desc("The file path to the input lcms file. This is a differential peak TSV file, for now."),

      // Note that the value separator is '|' because inchis can contain commas!
      CliOption.builder(OPTION_TARGET_INCHIS).
        hasArgs().valueSeparator('|').
        longOpt("target-inchis").
        desc("The target inchis to find precursors for.")
        required,

      CliOption.builder(OPTION_NUM_STEPS).
        hasArg().
        longOpt("num-steps").
        desc("The number of levels of precursors to return").
        required,

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {

    val workingDirPath = cl.getOptionValue(OPTION_WORKING_DIRECTORY)
    val workingDir: File = new File(workingDirPath)
    if (!workingDir.exists()) {
      workingDir.mkdir()
    }

    val inputNetworkFile = new File(cl.getOptionValue(OPTION_INPUT_NETWORK))
    verifyInputFile(inputNetworkFile)

    val inputLcms =
      if (cl.hasOption(OPTION_INPUT_LCMS)) {
        new File(cl.getOptionValue(OPTION_INPUT_LCMS))
      } else {
        null
      }

    val numSteps = Integer.parseInt(cl.getOptionValue(OPTION_NUM_STEPS))

    val precursorAnalysis = new PrecursorAnalysis(
      inputNetworkFile, Optional.ofNullable(inputLcms), cl.getOptionValues(OPTION_TARGET_INCHIS).toList.asJava, numSteps, workingDir)

    headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("PrecursorAnalysis", precursorAnalysis))
    headerJob
  }
}
