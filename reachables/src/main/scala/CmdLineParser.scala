package act.shared

import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager

class OptDesc(val param: String, val longParam: String, name: String, desc: String, 
              isReqd: Boolean = false, hasArg: Boolean = false, hasArgs: Boolean = false, sep: Char = ',') {
  // This class holds POSIX style short argument (single character, e.g., `-f brilliant.txt`)
  // And also GNU-style long argument (multiple character, e.g., `--output-file=brilliant.txt`
  // http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html

  val opt: CliOption.Builder = {

    // POSIX style arguments need to be single character
    assert (param.length == 1)
    
    // GNU style arguments cannot have any spaces in them
    assert ((longParam indexOf ' ') == -1)

    val built = CliOption.builder(param).
      required(isReqd).
      longOpt(longParam).
      desc(desc.replaceAll("\\s+", " ")).
      argName(name).
      hasArg(hasArg)

    // if this arg accepts multiple values, we add hasArgs.valueSeparator
    if (hasArgs)
      built.
      hasArgs.
      valueSeparator(sep)
    else
      built
  }
}

// TODO: rename this to CmdLine. But doing so requires us to nuke the current
// `cmdline.scala` in the repo. Will do that in the next PR
class CmdLineParser(parent: String, args: Array[String], optDescs: List[OptDesc], val helpMessage: String = "") {

  private val helpFormatter = new HelpFormatter
  helpFormatter.setWidth(100)
  private val logger = LogManager.getLogger(parent)

  // disallow param `h` since that is auto populated
  if (optDescs.exists(o => o.param.equals("h") || o.longParam.equals("help"))) {
    logger.error(s"The help option is pre-populated in the command line processor. Double help not needed!")
    throw new Exception("double help!")
  }

  // store the option descriptions. allows us to retrieve cmd line params
  // using either the full opt object, the single char, or long name
  private val supportedOpts = optDescs

  private val cmdline: CommandLine = parseCommandLineOptions(args) 

  def get(opt: OptDesc): String = get(opt.param)
  def get(option: String): String = getHasHelper[String](option, cmdline.getOptionValue)

  def getMany(opt: OptDesc): Array[String] = getMany(opt.param)
  def getMany(option: String): Array[String] = getHasHelper[Array[String]](option, cmdline.getOptionValues)

  def has(opt: OptDesc): Boolean = has(opt.param)
  def has(option: String): Boolean = getHasHelper[Boolean](option, cmdline.hasOption)
  
  private def getHasHelper[T](option: String, outfn: String => T): T = {
    val whichOpt: Option[OptDesc] = option.length match {
      case 1 => supportedOpts.find(_.param.equals(option))
      case _ => supportedOpts.find(_.longParam.equals(option))
    }

    val optStr = whichOpt match {
      case Some(o) => o.param
      case None => throw new Exception("You asked for a longparam that is not present " + 
                                       "in the option descriptions. Code failure. Please fix!")
    }

    outfn(optStr)
  }

  private def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        logger.error(s"Argument parsing failed: ${e.getMessage}")
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

  private def getCommandLineOptions: Options = {
    val helpOpt = new OptDesc(
                    param = "h",
                    longParam = "help",
                    name = "help",
                    desc = "Prints this help message") 
    val options = (helpOpt :: supportedOpts).map(_.opt)

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def exitWithHelp(opts: Options): Unit = {
    helpFormatter.printHelp(parent, helpMessage, opts, null, true)
    System.exit(1)
  }
}
