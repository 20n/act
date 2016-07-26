package com.act.analysis.chemicals

import java.io.{File, FileOutputStream, PrintStream}

import chemaxon.clustering.{JKlustorImport, LibraryMCS}
import chemaxon.formats.MolExporter
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable

/**
  * Implements key modifications to the LibraryMCS workflow to make the return CSV more ammenable to use afterwards.
  *
  * Now, input INCHIs are directly mapped onto output clusters, in addition to the traditional SMILEs being added to the output.
  * Outputs are no longer in a hierarchy, but only input INCHIs are linked to their cluster.
  */
object InchiClustering {

  val INPUT_FILE_PREFIX = "i"
  val OUTPUT_FILE_PREFIX = "o"
  val HELP_PREFIX = "h"

  val HELP_MESSAGE = "Clusters a list of InChIs by chemical similarity and outputs discrete cluster groups."
  val logger = LogManager.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {

    val cl = parseOptions(args)

    val inputFileName = cl.getOptionValue(INPUT_FILE_PREFIX)
    val outputFileName = cl.getOptionValue(OUTPUT_FILE_PREFIX)

    // Map the line number an InChI was on to the full InChI
    val listOfInchis = scala.io.Source.fromFile(inputFileName).getLines()
    var count = 0
    val insertionMap = new mutable.HashMap[String, String]
    while (listOfInchis.hasNext) {
      val n = listOfInchis.next()
      insertionMap.put(count.toString, n)
      count += 1
    }

    logger.info("Creating chemical clusters")
    // Cluster based on LibMcs
    val libMCS = new LibraryMCS
    val jci = new JKlustorImport(libMCS, null)
    jci.readStructures(JKlustorImport.getInputStream(inputFileName, false))
    libMCS.search()
    val results = libMCS.getClusterEnumerator(false)

    val cleanedOutputFileName = if (outputFileName.endsWith(".tsv")) outputFileName else outputFileName + ".tsv"
    logger.info(s"Saving results to output file $cleanedOutputFileName.")
    writeOutputTSV(new File(cleanedOutputFileName), results, insertionMap.toMap)
  }

  /**
    * Takes the results of the LibMCS search, a map of inchis to their positions in the original file,
    * and an output file and creates the final TSV summarizing the run by assigning a cluster for each InChI.
    *
    * @param outputFile - File that information will be streamed to
    * @param results    - LibMCS results
    * @param inchiMap   - {Location in file by number -> InChI string} mapping
    */
  def writeOutputTSV(outputFile: File, results: LibraryMCS#ClusterEnumerator, inchiMap: Map[String, String]): Unit = {
    // We see \t creates a TSV.  this is important is inchis contain commas, which can mess with creating a CSV file.
    val delimiter = "\t"

    // Keywords
    val inchi = "InChI"
    val smiles = "SMILES"
    val cluster = "Cluster"

    // Construct output stream
    val out = new FileOutputStream(outputFile)
    val outs = new PrintStream(out)

    // Write header
    outs.println(formatOutput(delimiter, smiles, inchi, cluster))

    // Go through all the clusters
    while (results.hasNext) {
      val molecule = results.next()

      val hierId = molecule.getPropertyObject("HierarchyID").asInstanceOf[String]
      val id = molecule.getPropertyObject("id").asInstanceOf[String]

      // Level = 1 is a new cluster, as it indicates the start of a hierarchy.
      val cluster_number = hierId.toString.split('.')(0)

      // If the ID matches one of the input file line numbers,
      // we can grab it back out by the ID it was assigned and write it.
      if (inchiMap.get(id).isDefined) {
        val outputLine = formatOutput(delimiter,
          MolExporter.exportToFormat(molecule, smiles), inchiMap.get(id).get, cluster_number.toString)
        logger.info(s"Saved line $outputLine.")
        outs.println(outputLine)
      }
    }

    outs.close()
  }

  /**
    * Format a string such that it has a delimiter between each member
    *
    * @param delimiter Given string delimiter
    * @param args      Sequence of strings
    *
    * @return String where each member of args is divided by a delimiter
    */
  def formatOutput(delimiter: String, args: String*): String = {
    val s = new StringBuilder
    for (arg <- args) {
      s.append(arg)
      s.append(delimiter)
    }
    s.toString()
  }

  /**
    * Parses the command line options based on the options
    *
    * @param args Args passed from Main
    *
    * @return A constructed CommandLine object
    */
  def parseOptions(args: Array[String]): CommandLine = {
    val opts = getOptions

    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args))
    } catch {
      case e: ParseException =>
        logger.error(s"Argument parsing failed: ${e.getMessage}\n")
        exitWithHelp()
    }
    if (cl.isEmpty) {
      logger.error("Detected that command line parser failed to be constructed.")
      exitWithHelp()
    }
    if (cl.get.hasOption("help")) exitWithHelp()

    // Is defined == True if got here (Not empty).
    cl.get
  }

  /**
    * Exits with an error code, but prints the help message first.
    */
  def exitWithHelp(): Unit = {
    val HELP_FORMATTER: HelpFormatter = new HelpFormatter
    HELP_FORMATTER.setWidth(100)
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, getOptions, null, true)
    System.exit(1)
  }

  /**
    * Command line options
    *
    * @return A constructed Options which contains the options already built.
    */
  def getOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(INPUT_FILE_PREFIX).
        required(true).
        hasArg.
        longOpt("input-file-location").
        desc("Input file containing InChIs, one per line."),

      CliOption.builder(OUTPUT_FILE_PREFIX).
        required(true).
        hasArg.
        longOpt("output-file-location").
        desc("Output TSV file containing inchis mapped to clusters."),

      CliOption.builder(HELP_PREFIX).
        longOpt("help").
        desc("Get help.")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }
}
