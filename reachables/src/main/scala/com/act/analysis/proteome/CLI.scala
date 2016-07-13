package com.act.analysis.proteome

import java.util

import com.act.analysis.proteome.tool_manager.workflow.{ExampleWorkflow, Workflow}
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import spray.http.CacheDirectives.public

import scala.collection.mutable

object CLI {
  private val OPTION_WORKFLOW = "w"

  def main(args: Array[String]): Unit = {
    require(args.length >= 1)

    val workflowName = args(0)

    if (workflowName.equals("-h") | workflowName.equals("help") | workflowName.equals("--help")){
      println("Help:")
      println("Define a workflow by passing WorkflowCLI <WorkflowName> to the command line")
      println("To request help for a specific workflow, pass WorkflowCLI <WorkflowName> -h")

    }
    val workflowClass: Class[_] = Class.forName(s"com.act.analysis.proteome.tool_manager.workflow.$workflowName")

    val workflow = workflowClass.newInstance().asInstanceOf[Workflow]
    workflow.parseArgs(args.slice(1, args.length).toList)




    println(workflowClass)
    println(workflow)


  }

  val OPTION_BUILDERS: List[Option.Builder] = List[Option.Builder](
      Option.builder(OPTION_WORKFLOW).argName("Chosen workflow.").desc("The workflow you would like to run.").hasArg().longOpt("target-workflow")
  )
}
