package com.act.reachables

import java.io.{File, PrintWriter}

import act.server.MongoDB
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.io.Source

object reachables {
  private val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)
  val logger = LogManager.getLogger(getClass.getName)

  private val HELP_MESSAGE =
    """Usage: run --prefix=PRE --hasSeq=true|false --regressionSuiteDir=path --extra=[semicolon-sep db.chemical fields]
      |
      |Example: run --prefix=r
      | will create reachables tree with prefix r and by default with only enzymes that have seq
      |
      |Example: run --prefix=r --extra=xref.CHEBI;xref.DEA;xref.DRUGBANK
      | will make sure all CHEBI/DEA/DRUGBANK chemicals are included. Those that are already reachable will be in the
      | normal part of the tree and those that are not will have parent_id < -1 """.stripMargin

  private val OPTION_PREFIX = "p"
  private val OPTION_HAS_SEQ = "s"
  private val OPTION_NATIVES_FILE = "n"
  private val OPTION_COFACTORS_FILE = "c"
  private val OPTION_REGRESSION_DIR = "r"

  private def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_PREFIX).
        hasArg.
        longOpt("prefix").
        desc("Prefix applied to any file generated").
        required(true),

      CliOption.builder(OPTION_HAS_SEQ).
        longOpt("hasSeq").
        desc("Flag to indicate if only reactions with sequences should be used."),

      CliOption.builder(OPTION_NATIVES_FILE).
        longOpt("useNativesFile").
        desc("Path to file containing native chemicals."),

      CliOption.builder(OPTION_COFACTORS_FILE).
        longOpt("useCofactorsFile").
        desc("Path to file containing cofactor chemicals."),

      CliOption.builder(OPTION_REGRESSION_DIR).
        longOpt("regressionSuiteDir").
        desc("Path to the directory that regression suite files should be written to."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  private def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
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

  def main(args: Array[String]) {
    val params = parseCommandLineOptions(args)

    val prefix = params.getOptionValue(OPTION_PREFIX)

    writeReachableTree(prefix,
      params.hasOption(OPTION_HAS_SEQ),
      Option(params.getOptionValue(OPTION_NATIVES_FILE)),
      Option(params.getOptionValue(OPTION_COFACTORS_FILE)))
  }

  def writeReachableTree(prefix: String, needSeq: Boolean, nativesFile: Option[String], cofactorsFile: Option[String]) {
    val rdir = prefix + ".regressions/" // regression output directory

    // Connect to the DB so that extended attributes for chemicals can be fetched as we serialize.
    val db = new MongoDB("localhost", 27017, "marvin")


    val universal_natives = if (nativesFile.isDefined) {
      val data = Source.fromFile(nativesFile.get).getLines
      val inchis = data.filter { x => x.length > 0 && x.charAt(0) != '#' }
      collection.mutable.Set(inchis.toSeq: _*)
    } else {
      null
    }

    val universal_natives = if (cofactorsFile.isDefined) {
      val data = Source.fromFile(cofactorsFile.get).getLines
      val inchis = data.filter { x => x.length > 0 && x.charAt(0) != '#' }
      collection.mutable.Set(inchis.toSeq: _*).toSet
    } else {
      null
    }


    val regression_suite_files =
      opts.get("regressionSuiteDir") match {
        case Some(dir) => {
          val files = new File(dir).listFiles
          val testfiles = files
            .map(n => n.getAbsolutePath)
            .filter(_.endsWith(".test.txt"))
          testfiles.toSet
        }
        case _ => Set()
      }

    val chems_w_extra_fields =
      opts.get("extra") match {
        case Some(fields) => fields split ";"
        case None => null
      }

    // set parameter for whether we want to exclude rxns that dont have seq
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = needSeq

    // compute the reachables tree!
    val restrict_to_enzymes_that_have_seqs = false
    val tree = LoadAct.getReachablesTree(universal_natives,
      universal_cofactors,
      restrict_to_enzymes_that_have_seqs,
      chems_w_extra_fields)
    println("Done: L2 reachables computed. Num reachables found: " + tree.nodesAndIds.size)

    // get inchis for all the reachables
    // Network.nodesAndIds(): Map[Node, Long] i.e., maps nodes -> ids
    // chemId2Inchis: Map[Long, String] i.e., maps ids -> inchis
    // so we take the Map[Node, Long] and map each of the key_value pairs
    // by looking up their ids (using n_ids._2) in chemId2Inchis
    // then get a nice little set from that Iterable
    def id2InChIName(id: Long) = id ->(ActData.instance.chemId2Inchis.get(id), ActData.instance.chemId2ReadableName.get(id))
    def tab(id_inchi_name: (Long, (String, String))) = id_inchi_name._1 + "\t" + id_inchi_name._2._2 + "\t" + id_inchi_name._2._1

    val reachables: Map[Long, (String, String)] = tree.nodesAndIds.map(x => id2InChIName(x._2)).toMap
    def fst(x: (String, String)) = x._1
    val r_inchis: Set[String] = reachables.values.toSet.map(fst) // reachables.values are (inchi, name)

    // create output directory for regression test reports, if not already exists
    makeRegressionDirectory(rdir)
    // run regression suites if provided
    regression_suite_files.foreach(test => runRegression(r_inchis, test, rdir))

    // serialize ActData, which contains a summary of the relevant act
    // data and the corresponding computed reachables state
    ActData.instance.serialize(prefix + ".actdata")
  }

  def runRegression(reachable_inchis: Set[String], test_file: String, output_report_dir: String) {
    val testlines: List[String] = Source.fromFile(test_file).getLines.toList
    val testcols: List[List[String]] = testlines.map(line => line.split("\t").toList)

    val hdrs = Set("inchi", "name", "plausibility", "comment", "reference")
    if (testcols.isEmpty || !testcols.head.toSet.equals(hdrs)) {
      println("Invalid test file: " + test_file)
      println("\tExpected: " + hdrs.toString)
      println("\tFound: " + testcols.head.toString)
    } else {

      // delete the header from the data set, leaving behind only the test rows
      val hdr = testcols.head
      val rows = testcols.drop(1)

      def add_hdrs(row: List[String]) = hdr.zip(row)

      // partition test rows based on whether this reachables set passes or fails them
      val (passed, failed) = rows.partition(testrow => runRegression(add_hdrs(testrow), reachable_inchis))

      val report = generateReport(test_file, passed, failed)
      writeTo(output_report_dir + "/" + new File(test_file).getName, report)
      println("Regression file: " + test_file)
      println("Total test: " + rows.length + " (passed, failed): (" + passed.length + ", " + failed.length + ")")
    }
  }

  def makeRegressionDirectory(dir: String) {
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need it as dir for regression output. Abort.")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }
  }

  def generateReport(f: String, passed: List[List[String]], failed: List[List[String]]) = {
    val total = passed.length + failed.length
    val write_successes = false
    val write_failures = true

    val lines =
    // add summary to head of report file
      List(
        "** Regression test result for " + f,
        "\tTOTAL: " + total + " PASSED: " + passed.length,
        "\tTOTAL: " + total + " FAILED: " + failed.length
      ) ++ (
        // add details of cases that succeeded
        if (write_successes)
          passed.map("\t\tPASSED: " + _).toList
        else
          List()
        ) ++ (
        // add details of cases that failed
        if (write_failures)
          failed.map("\t\tFAILED: " + _).toList
        else
          List()
        )

    // make the report as a string of lines
    val report = lines reduce (_ + "\n" + _)

    // return the report
    report
  }

  def runRegression(row: List[(String, String)], reachable_inchis: Set[String]): Boolean = {
    val data = row.toMap
    val inchi = data.getOrElse("inchi", "") // inchi column
    val should_exist = data.getOrElse("plausibility", "TRUE").toBoolean // plausibility column

    val exists = reachable_inchis.contains(inchi)

    if (should_exist)
      exists // if should-exist then output whether it exists
    else
      !exists // if should-not-exist then output negation of it-exists in reachable
  }

  def writeTo(fname: String, json: String) {
    val file = new PrintWriter(new File(fname))
    file write json
    file.close()
  }
}
