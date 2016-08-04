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
