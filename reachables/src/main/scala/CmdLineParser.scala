package act.shared

import java.lang.Class
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager

class OptDesc(val param: String, val longParam: String, name: String, desc: String, 
              isReqd: Boolean = false, hasArg: Boolean = false) {
  // This class holds POSIX style short argument (single character, e.g., `-f brilliant.txt`)
  // And also GNU-style long argument (multiple character, e.g., `--output-file=brilliant.txt`
  // http://www.gnu.org/software/libc/manual/html_node/Argument-Syntax.html

  val opt: CliOption.Builder = {

    // POSIX style arguments need to be single character
    assert (param.length == 1)
    
    // GNU style arguments cannot have any spaces in them
    assert ((longParam indexOf ' ') == -1)

    CliOption.builder(param).
      required(isReqd).
      longOpt(longParam).
      desc(desc).
      argName(name).
      hasArg(hasArg)
  }
}

// TODO: rename this to CmdLine. But doing so requires us to nuke the current
// `cmdline.scala` in the repo. Will do that in the next PR
class CmdLineParser(parent: String, args: Array[String], optDescs: List[OptDesc]) {

  val help_formatter: HelpFormatter = new HelpFormatter
  val help_message = ""
  help_formatter.setWidth(100)
  private val logger = LogManager.getLogger(parent)

  // store the option descriptions. allows us to retrieve cmd line params
  // using either the full opt object, the single char, or long name
  val supportedOpts = optDescs

  val cmdline: CommandLine = parseCommandLineOptions(args) 

  def get(opt: OptDesc): String = get(opt.param)
  def get(option: String): String = getHasHelper[String](option, cmdline.getOptionValue)

  def has(opt: OptDesc): Boolean = has(opt.param)
  def has(option: String): Boolean = getHasHelper[Boolean](option, cmdline.hasOption)
  
  def getHasHelper[T](option: String, outfn: String => T): T = {
    option.length match {
      case 1 => {
        // short arg, look it up directly
        outfn(option)
      }
      case _ => {
        // if long opt given then look up the corresponding single char param
        supportedOpts.find(_.longParam.equals(option)) match {
          case Some(o) => outfn(o.param)
          case None => throw new Exception("You asked for a multiple-param that is not present " + 
                                            "in the option descriptions. Code failure. Please fix!")
        }
      }
    } 
  }

  def parseCommandLineOptions(args: Array[String]): CommandLine = {
    val opts = getCommandLineOptions

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

  def getCommandLineOptions: Options = {
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
    help_formatter.printHelp(parent, help_message, opts, null, true)
    System.exit(1)
  }
}
