package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException}

trait Workflow {
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)
  val HELP_MESSAGE = ""

  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(commandLine: CommandLine): Job

  // This workflow will block all other execution until all queued jobs complete.
  def startWorkflow(args: List[String]): Unit = {
    val commandLine = parseCommandLineOptions(args.toArray)
    val firstJob = defineWorkflow(commandLine)

    firstJob.start()
    JobManager.awaitUntilAllJobsComplete()
  }

  def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        JobManager.logError(s"Argument parsing failed: ${e.getMessage}\n")
        exitWithHelp(opts)
    }

    if (cl.isEmpty) {
      JobManager.logError("Detected that command line parser failed to be constructed.")
      exitWithHelp(opts)
    }

    if (cl.get.hasOption("help")) exitWithHelp(opts)

    cl.get
  }

  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  def getCommandLineOptions: Options = {
    new Options()
  }
}

