package com.act.analysis.proteome

import com.act.analysis.proteome.tool_manager.workflow.Workflow

object CLI {
  // This is only as up to date as the programmer is
  val AVAILABLE_WORKFLOWS = List[String]("ExampleWorkflow", "RoToProteinPredictionFlow")

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
      val workflowClass: Class[_] = Class.forName(s"com.act.analysis.proteome.tool_manager.workflow.$workflowName")
      val workflow = workflowClass.newInstance().asInstanceOf[Workflow]

      // Pass the rest of the args to the workflow to parse and start the workflow
      workflow.parseArgs(args.slice(1, args.length).toList)
      workflow.startWorkflowBlocking()
    } catch {
      case e: ClassNotFoundException => println(s"Available workflows are $AVAILABLE_WORKFLOWS")
    }
  }
}
