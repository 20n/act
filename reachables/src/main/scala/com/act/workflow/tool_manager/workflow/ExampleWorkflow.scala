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

package com.act.workflow.tool_manager.workflow

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.ShellWrapper
import org.apache.commons.cli.CommandLine


class ExampleWorkflow extends Workflow {
  def defineWorkflow(commandLine: CommandLine): Job = {
    // Print working directory
    val job1 = ShellWrapper.shellCommand("Print Working Directory", List("pwd"))

    // See which files are available
    val job2 = ShellWrapper.shellCommand("List files", List("ls"))

    // Get today's date
    val job3 = ShellWrapper.shellCommand("Vanilla date", List("date"))

    // Print date as just the hour
    val job4 = ShellWrapper.shellCommand("Date with hours", List("date", "+%H"))

    // Check directory again
    val job5 = ShellWrapper.shellCommand("Print Working Directory", List("pwd"))

    // Returns first job
    job1.thenRunBatch(List(job2, job3)).thenRun(job4).thenRun(job5)
  }
}
