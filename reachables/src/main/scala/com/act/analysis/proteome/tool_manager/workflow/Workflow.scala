package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}
import org.apache.commons.cli.{HelpFormatter, Options}

trait Workflow {
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)
  val HELP_MESSAGE = ""

  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(context: Map[String, Option[List[String]]]): Job

  // This workflow will block all other execution until all queued jobs complete.
  def startWorkflow(args: List[String]): Unit = {
    val context = parseArgs(args)
    val firstJob = defineWorkflow(context)

    firstJob.start()
    JobManager.awaitUntilAllJobsComplete()
  }

  def parseArgs(args: List[String]): Map[String, Option[List[String]]] = {
    // No args = no context map
    Map[String, Option[List[String]]]()
  }
}

