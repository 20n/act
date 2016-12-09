package com.act.analysis.proteome.scripts

import java.io.{BufferedWriter, File, FileNotFoundException, FileWriter}
import java.util.concurrent.atomic.AtomicInteger

import act.shared.{Seq => DbSeq}
import com.act.analysis.proteome.files.HmmResultParser
import com.act.workflow.tool_manager.tool_wrappers.HmmerWrapper
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.ConditionalToSequence
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.LogManager
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.sys.process._

object OddSequencesToProteinPredictionFlow extends ConditionalToSequence {

  val HELP_MESSAGE = "Uses a set of reference proteomes to assign full sequences to database entries that are 'odd' (incomplete)."
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)
  val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_COMPARE_PROTEOME_LOCATION = "l"
  private val OPTION_DATABASE = "d"
  private val found = new AtomicInteger()
  private val processed = new AtomicInteger()
  private val foundWithSequenceInferred = new AtomicInteger()
  private val counterDisplayRate = 100
  private var idToOrganism: Map[Long, String] = Map()
  private var orgsToProteomes: Map[String, List[File]] = Map()

  def main(args: Array[String]): Unit = {
    defineWorkflow(parseCommandLineOptions(args))
  }

  private def parseCommandLineOptions(args: Array[String]): CommandLine = {
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

  private def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION).
        longOpt("reference-proteomes-directory").
        hasArg.
        desc("The file path of the proteome directory containing all the reference proteomes").
        required(true),

      CliOption.builder(OPTION_DATABASE).
        longOpt("database").
        hasArg.desc("The name of the MongoDB to use for this query.").
        required(true),

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

  private def defineWorkflow(cl: CommandLine): Unit = {
    /* - - - - Parse CLI Options - - - - */
    val workingDir = new File(cl.getOptionValue(OPTION_WORKING_DIRECTORY, null))
    val proteomeLocation = new File(cl.getOptionValue(OPTION_COMPARE_PROTEOME_LOCATION))
    if (!proteomeLocation.exists()) throw new FileNotFoundException(s"Proteome location of ${proteomeLocation.getAbsolutePath} does not exist.")
    val database = cl.getOptionValue(OPTION_DATABASE)

    /* - - - - Setup Directory structure - - - - */
    // Note: These are all temporary given we are writing everything to a DB in the end.
    val fastaDirectory = setupDirectory(workingDir, "fastaFiles")
    val resultHmmDirectory = setupDirectory(workingDir, "resultFiles")
    val tempSeqDbDir = setupDirectory(workingDir, "tempSeqDb")

    /* - - - - Discover relevant sequence entries, put their organisms into a lookup table - - - - */
    val organismProteomes = proteomeLocation.listFiles().toList
    orgsToProteomes = classifyOrganismByProteome(organismProteomes)

    val oddCriteria = "this.seq.length < 80 || this.seq[0] != 'M'"
    logger.info(s"Defining sequences that match odd criteria of $oddCriteria")
    val matchingSequences: Stream[DbSeq] = getIdsForEachDocumentInConditional(database)(oddCriteria)

    // Just the DB Sequence ID => String name of that organism
    idToOrganism = matchingSequences.map(doc => {
      val id = doc.getUUID.toLong
      val org = doc.getOrgName
      (id, org)
    }).toMap

    val oddIds = idToOrganism.keys.toList
    logger.info(s"Found ${oddIds.length} ids that match criteria.")

    /* - - - - Counters used to track our progress as we go - - - - */
    val sequenceSearch: (DbSeq) => Unit = defineSequenceSearch(fastaDirectory, resultHmmDirectory, tempSeqDbDir)(proteomeLocation)(database)
    matchingSequences.foreach(sequenceSearch)

    // Cleanup our file structure
    FileUtils.deleteDirectory(tempSeqDbDir)
    FileUtils.deleteDirectory(fastaDirectory)
    FileUtils.deleteDirectory(resultHmmDirectory)
  }

  private def setupDirectory(parent: File, dirName: String): File = {
    val newDirectory = new File(parent, dirName)
    if (!newDirectory.exists()) newDirectory.mkdirs()
    newDirectory
  }

  private def classifyOrganismByProteome(files: List[File]): Map[String, List[File]] = {
    /*
      >sp|Q0EAB6|5HT1A_HORSE 5-hydroxytryptamine receptor 1A OS=Equus caballus GN=HTR1A PE=2 SV=1
      >                                                         ^             ^
      >                                                          -------------
      >                                                         String of interest
     */
    val fileMap = files.map(f => {
      val firstLine = scala.io.Source.fromFile(f).getLines().next()

      // Take everything after the "OS", that's the organism
      val tailOfLine = firstLine.split("OS=")(1)

      // Just get the first two strings.  There may be more, but usually the important bits are in the first two.
      val organism = tailOfLine.split(" ").take(2).mkString(" ")

      (organism, f)
    })

    val firstLevelMap = new scala.collection.mutable.HashMap[String, ListBuffer[File]]()

    fileMap.foreach({case(organism, file) =>
      if (!firstLevelMap.contains(organism)) {
        firstLevelMap.put(organism, ListBuffer[File]())
      }
      val firstWordOrg = organism.split(" ")(0)
      if (!firstLevelMap.contains(firstWordOrg)) {
        firstLevelMap.put(firstWordOrg, ListBuffer[File]())
      }

      firstLevelMap(organism).append(file)
      firstLevelMap(firstWordOrg).append(file)
    })

    firstLevelMap.map({case(key, value) => (key, value.toList)}).toMap
  }

  def defineSequenceSearch(resultHmmDirectory: File, fastaDirectory: File, tempSeqDbDir: File)
                          (proteomeLocation: File)
                          (database: String)
                          (sequence: DbSeq): Unit = {
    // TODO This function currently uses flat files to communicate to CLI, instead of directly transferring them in
    // via stdio and it also parses a flat file instead of doing parsing on the output stream from stdout which
    // could be possible.  This is likely the slowest part and easiest optimization if needed moving forward.

    /* - - - - Setup temp files that will hold the data prior to database upload - - - - */
    val prefix = sequence.getUUID.toString
    val resultFilePath = new File(resultHmmDirectory, s"$prefix.output.search.result")
    val outputFastaPath = new File(fastaDirectory, s"$prefix.output.fasta")
    val tempProteomeFile = new File(tempSeqDbDir, s"$prefix.tmp_seq_db")

    if (processed.incrementAndGet() % counterDisplayRate == 0) {
      logger.info(s"Found reference proteome for ${found.get()} " +
        s"(${foundWithSequenceInferred.get()} with inferred sequences) out of ${processed.get()} sequences")
    }

    // Create the FASTA file out of the database sequence
    writeFastaFileForEachDocument(database, outputFastaPath)(sequence.getUUID.toLong)

    // Figure out which reference proteomes should be used for this sequence
    val proteomesToQueryAgainst = getMatchingProteomes(sequence)
    if (proteomesToQueryAgainst.isEmpty) return
    createUberProteome(tempProteomeFile, proteomesToQueryAgainst.get)

    /* - - - - Use Phmmer to find matching/close sequences - - - - */
    // We grab the exit code to ensure that the process has completed before deleting the file.
    List(HmmerWrapper.HmmCommands.Phmmer.getCommand, "-o", resultFilePath.getAbsolutePath, outputFastaPath.getAbsolutePath,
      tempProteomeFile.getAbsolutePath).run().exitValue()

    // These files are no longer needed, so we delete them.
    FileUtils.deleteQuietly(outputFastaPath)
    FileUtils.deleteQuietly(tempProteomeFile)

    /* - - - - Take the HMM result and parse the source sequence file for the sequence - - - - */
    val sourceSequenceLinks = getRelevantSequencesFromPhmmerResult(resultFilePath, proteomeLocation)
    val resultingSequences = parseResultSequences(sourceSequenceLinks)

    /* - - - - Do the update to the database - - - - */
    val mongoDatabaseConnection = connectToMongoDatabase(database)
    val metadata = sequence.getMetadata
    val inferArray = new JSONArray()
    val jsons = resultingSequences.asJavaCollection.map(x => x.asJson)
    jsons.foreach(inferArray.put)

    if (resultingSequences.nonEmpty) {
      foundWithSequenceInferred.incrementAndGet()
    }

    metadata.put("inferred_sequences", inferArray)
    sequence.setMetadata(metadata)

    mongoDatabaseConnection.updateMetadata(sequence)

    FileUtils.deleteQuietly(resultFilePath)
    logger.info(s"Finished inferring additional sequences for sequence ${sequence.getUUID}")
    found.incrementAndGet()
  }

  private def getMatchingProteomes(sequence: DbSeq): Option[List[File]] = {
    val organism = idToOrganism(sequence.getUUID.toLong)
    val firstOrgWord = organism.split(" ")(0)

    if (orgsToProteomes.contains(organism)) {
      orgsToProteomes.get(organism)
    } else if (orgsToProteomes.contains(firstOrgWord)) {
      orgsToProteomes.get(firstOrgWord)
    } else {
      // Select the correct file to search based on the organism
      // TODO Handle when we don't have a matching proteome
      None
    }
  }

  private def createUberProteome(uberProteomeFileLocation: File, files: List[File]): Unit = {
    /*
      Sometimes, there might be matching organism proteomes
      (Multiple strains or genus-level matching,
      so we make one file containing all of them
    */
    val writer = new BufferedWriter(new FileWriter(uberProteomeFileLocation))
    files.foreach(f => {
      scala.io.Source.fromFile(f).getLines.foreach(l => {
        // Fasta headers start with '>', indicating a new sequence is being shown.
        val currentLine = if (l.startsWith(">")) {
          // Keep a reference to the FASTA file that this sequence is in by referencing it in the fasta header
          s">${f.getName} | ${l.replaceFirst(">", "")}\n"
        } else {
          s"$l\n"
        }
        writer.write(currentLine)
      })
    })
    writer.close()
  }

  private def getRelevantSequencesFromPhmmerResult(resultFilePath: File, proteomeLocation: File): List[SequenceConnection] = {
    val parsedFile = HmmResultParser.parseFile(resultFilePath)
    parsedFile.map(p =>
      SequenceConnection(
        new File(proteomeLocation, p(HmmResultParser.HmmResultLine.SEQUENCE_NAME)),
        // Pipe needs to be a character otherwise it will be processed as regex.
        //
        // This trimming is necessary as we stored the file location in the fasta header previously,
        // but we don't want that anymore.
        p(HmmResultParser.HmmResultLine.DESCRIPTION).split('|').tail.mkString("|").trim,
        p(HmmResultParser.HmmResultLine.SCORE_DOMAIN).toDouble,
        p(HmmResultParser.HmmResultLine.SCORE_FULL_SEQUENCE).toDouble
      ))
  }

  private def parseResultSequences(sourceSequenceLinks: List[SequenceConnection]): List[SequenceEntry] = {
    // Use the inferred connection information to lookup the original sequence in the original file
    sourceSequenceLinks.map(link => {
      val originFile = scala.io.Source.fromFile(link.originFile)

      val lines = originFile.getLines()
      // We find the FASTA header that matches our current sequence
      val result = lines.span(!_.contains(link.sequenceName))
      // Group 1 has everything prior to the stop parsing indicator

      val header = result._2.next
      val resultProtein = result._2.span(l => !l.startsWith(">"))
      // Link the entire sequence together, as it was likely on multiple lines.
      val sequence = resultProtein._1.mkString("")

      originFile.close()
      SequenceEntry(header, sequence, link.scoreDomain, link.scoreFullSequence, link.originFile)
    })
  }

  // Link between HMM result and the sequences file that the sequence originates from.
  case class SequenceConnection(originFile: File, sequenceName: String, scoreDomain: Double, scoreFullSequence: Double)

  case class SequenceEntry(fastaHeader: String, sequence: String, scoreDomain: Double, scoreFullSequence: Double, sourceFile: File) {
    def asJson: JSONObject = {
      val thisAsJson = new JSONObject()
      thisAsJson.put("fasta_header", fastaHeader)
      thisAsJson.put("sequence", sequence)
      thisAsJson.put("sequence_length", sequence.length)
      thisAsJson.put("hmmer_domain_score", scoreDomain)
      thisAsJson.put("hmmer_full_sequence_score", scoreFullSequence)
      thisAsJson.put("source_file_name", sourceFile.getName)

      thisAsJson
    }
  }
}
