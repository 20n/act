package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.management.JobManager
import com.act.analysis.proteome.tool_manager.jobs.{HeaderJob, Job}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException}
import org.apache.logging.log4j.LogManager

trait Workflow {
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE = ""
  HELP_FORMATTER.setWidth(100)

  // Header job allows us to have multiple start jobs all line up with this one.
  val headerJob = new HeaderJob()
  private val logger = LogManager.getLogger(getClass.getName)

  // Implement this with the job structure you want to run to define a workflow
  def defineWorkflow(commandLine: CommandLine): Job

  /**
    * This workflow will block all other execution until all queued jobs complete.
    *
    * @param args The command line args
    */
  def startWorkflow(args: List[String]): Unit = {
    val commandLine = parseCommandLineOptions(args.toArray)
    val firstJob = defineWorkflow(commandLine)

    firstJob.start()
    JobManager.awaitUntilAllJobsComplete()
  }

  /**
    * Take the options and parse
    *
    * @param args The command line args
    *
    * @return An instance of the CommandLine populated with the values
    */
  def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        logger.error(s"Argument parsing failed: ${e.getMessage}\n")
        exitWithHelp(opts)
    }

    if (cl.isEmpty) {
      logger.error("Detected that command line parser failed to be constructed.")
      exitWithHelp(opts)
    }

    if (cl.get.hasOption("help")) exitWithHelp(opts)

    logger.info("Finished processing command line information")
    cl.get
  }

  /**
    * Called if we want to exit, but give the user a help command to clarify
    *
    * @param opts The options for this workflow
    */
  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  /**
    * Default command line options (None).  Should be overriden to use custom options
    *
    * @return An Options type, empty
    */
  def getCommandLineOptions: Options = {
    new Options()
  }
}

