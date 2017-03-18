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

package com.act.workflow

import com.act.workflow.tool_manager.workflow.Workflow

object CLI {
  // This is only as up to date as we up date it when we add new workflows.
  val AVAILABLE_WORKFLOWS = List[String](
    "com.act.workflow.tool_manager.workflow.ExampleWorkflow",
    "com.act.analysis.proteome.proteome_workflow.RoToProteinPredictionFlow",
    "com.act.analysis.proteome.proteome_workflow.EcnumToProteinPredictionFlow",
    "com.act.analysis.proteome.proteome_workflow.EcnumToFastaFlow",
    "com.act.analysis.proteome.proteome_workflow.RoToFastaFlow"
  )

  def main(args: Array[String]): Unit = {
    // Workflow ID should always be first
    val workflowName = args(0)

    // Determine if the user is asking for help
    if (workflowName.equals("-h") | workflowName.equals("help")
      | workflowName.equals("--help")) {
      println("Quick help")
      println("Define a workflow by passing WorkflowCLI <WorkflowName> to the command line")
      println("To request help for a specific workflow, use WorkflowCLI <WorkflowName> -h")
      println(s"Available workflows are $AVAILABLE_WORKFLOWS")
      return
    }

    // Try to match the class in the workflow folder with the requested workflow.
    try {
      // Takes any workflow as the long class path
      val workflowClass: Class[_] = Class.forName(workflowName)
      val workflow = workflowClass.newInstance().asInstanceOf[Workflow]

      // Pass the rest of the args to the workflow to parse and start the workflow
      workflow.startWorkflow(args.slice(1, args.length).toList)

    } catch {
      case e: ClassNotFoundException => println(s"Available workflows are $AVAILABLE_WORKFLOWS")
    }
  }
}
