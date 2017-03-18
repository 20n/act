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

import com.act.biointerpretation.networkanalysis.GraphViz.{DotColor, PrecursorReportVisualizer}
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.JavaJobWrapper
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

class NetworkVisualizationFlow extends Workflow with WorkingDirectoryUtility {

  val logger = LogManager.getLogger(getClass.getName)

  private val OUTPUT_PREFIX = "graphViz_precursor_"

  override val HELP_MESSAGE = "Reads in a metabolism network and writes out its GraphViz representation."

  private val OPTION_WORKING_DIR = "w"
  private val OPTION_ORG_STRINGS = "s"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIR).
        hasArg.
        longOpt("working-dir").
        desc("The file path to the directory containing precursor target analyses. The outputs are written to the " +
          s"same directory, with prefix $OUTPUT_PREFIX").
        required(),

      CliOption.builder(OPTION_ORG_STRINGS).
        hasArg.
        longOpt("org-strings").
        desc(
          """One or more strings representing organisms of interest. Edges with orgs containing
            |these strings are colored in red.""".stripMargin),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def getOutputFile(workingDir: File, input: File): File = {
    val outputName = OUTPUT_PREFIX + input.getName.stripPrefix(PrecursorAnalysis.PRECURSOR_PREFIX)
    new File(workingDir, outputName)
  }

  override def defineWorkflow(cl: CommandLine): Job = {

    val workingDir = new File(cl.getOptionValue(OPTION_WORKING_DIR))
    createWorkingDirectory(workingDir)
    val inputs: Array[File] = workingDir.listFiles().filter(f => f.getName.contains(PrecursorAnalysis.PRECURSOR_PREFIX))

    val networkViz = new PrecursorReportVisualizer()
    if (cl.hasOption(OPTION_ORG_STRINGS)) {
      for (x <- cl.getOptionValues(OPTION_ORG_STRINGS)) {
        networkViz.addOrgOfInterest(x, DotColor.RED)
      }
    }

    val jobs = inputs.map(i => JavaJobWrapper.wrapJavaFunction("graphViz", networkViz.getRunner(i, getOutputFile(workingDir, i))))
    headerJob.thenRunBatch(jobs.toList)
    headerJob
  }
}
