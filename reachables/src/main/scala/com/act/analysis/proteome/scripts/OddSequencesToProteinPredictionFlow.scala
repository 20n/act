package com.act.analysis.proteome.scripts

import java.io.{File, FileNotFoundException, InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import act.shared.{Seq => DbSeq}
import com.act.analysis.proteome.files.HmmResultParser
import com.act.workflow.tool_manager.tool_wrappers.HmmerWrapper
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.ConditionalToSequence
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, SequenceKeywords}
import com.mongodb.{BasicDBList, BasicDBObject}
import org.apache.commons.cli.{CommandLine, DefaultParser, HelpFormatter, Options, ParseException, Option => CliOption}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.logging.log4j.LogManager
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
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
  private val counterDisplayRate = 1
  private val takeTopNumberOfResults = 5
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
        desc("Run and create all files from a working directory you designate.").
        required(true),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION).
        longOpt("reference-proteomes-directory").
        hasArg.
        desc("The file path of the proteome directory containing all the reference proteomes").
        required(true),

      CliOption.builder(OPTION_DATABASE).
        longOpt("database").
        hasArg.
        desc("The name of the MongoDB to use for this query.").
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

    /* - - - - Discover relevant sequence entries, put their organisms into a lookup table - - - - */
    val organismProteomes = proteomeLocation.listFiles().toList
    orgsToProteomes = classifyOrganismByProteome(organismProteomes)

    // Get relevant sequences
    val mongoConnection = connectToMongoDatabase(database)

    val oddCriteria = "this.seq.length < 80 || this.seq[0] != 'M'"
    val whereQuery = createDbObject(MongoKeywords.WHERE, oddCriteria)
    whereQuery.put(SequenceKeywords.SEQ.toString, createDbObject(MongoKeywords.NOT_EQUAL, null))

    // These wild card sequences have a much lower hit rate to be inferred.
    // Wildcard sequences are sequences which have "*" somewhere in the sequence, usually indicating ambiguity.
    // We are trying to disambiguate sequences, so it is within this context that we attempt to do so through HMMER.
    val wildcardQuery = new BasicDBObject()
    val wildcardSequencesRegex = createDbObject(MongoKeywords.REGEX, "\\*")
    wildcardQuery.put(SequenceKeywords.SEQ.toString, wildcardSequencesRegex)

    val conditionals = new BasicDBList
    conditionals.add(wildcardQuery)
    conditionals.add(whereQuery)

    val seqQuery = new BasicDBObject(MongoKeywords.OR.toString, conditionals)
    seqQuery.put(SequenceKeywords.SEQ.toString, createDbObject(MongoKeywords.NOT_EQUAL, null))

    val matchingSequences: Iterator[DbSeq] = mongoConnection.getSeqIterator(seqQuery).asScala

    /* - - - - Counters used to track our progress as we go - - - - */
    logger.info("Starting assignment of inferred sequences to odd database entries.")
    val sequenceSearch: (DbSeq) => Unit = defineSequenceSearch(fastaDirectory)(proteomeLocation)(database)

    // .par evaluates the iterator which takes a while (It is a mongoDB cursor movement).
    // We can get everything going right away AND in parallel by just making it into a future.
    val futureList = Future.traverse(matchingSequences)(x => Future(sequenceSearch(x)))

    Await.ready(futureList, Duration.Inf)

    // Cleanup our file structure
    FileUtils.deleteDirectory(fastaDirectory)
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
      if (!firstLine.contains("OS=")) {
        throw new RuntimeException(
          """Malformed FASTA file.
          | Files are required to have a 'OS=' member in their header designating the organism.
          | Reference Proteomes from Uniprot should have this characteristic by default.
          |
          | Example FASTA Header:
          | sp|Q0EAB6|5HT1A_HORSE 5-hydroxytryptamine receptor 1A OS=Equus caballus GN=HTR1A PE=2 SV=1
          """.stripMargin)
      }
      val tailOfLine = firstLine.split("OS=")(1)

      // Just get the first two strings.  There may be more, but usually the important bits are in the first two.
      val organism = tailOfLine.split(" ").take(2).mkString(" ")

      if (organism.contains("=")) throw new RuntimeException(s"Parsed organism name contains '=', please check input FASTA file ${f.getAbsolutePath}")

      (organism, f)
    })

    val firstLevelMap = new scala.collection.mutable.HashMap[String, ListBuffer[File]]()

    fileMap.foreach({case(organismName, file) =>
      // We first try to find the full organism name (2 words)
      if (!firstLevelMap.contains(organismName)) {
        firstLevelMap.put(organismName, ListBuffer[File]())
      }

      // If we can't find the full organism name, we try to salvage
      // it by just using the genus as a reference.
      val genusName = organismName.split(" ")(0)
      if (!firstLevelMap.contains(genusName)) {
        firstLevelMap.put(genusName, ListBuffer[File]())
      }

      firstLevelMap(organismName).append(file)
      firstLevelMap(genusName).append(file)
    })

    firstLevelMap.mapValues(_.toList).toMap
  }

  def defineSequenceSearch(fastaDirectory: File)
                          (proteomeLocation: File)
                          (database: String)
                          (sequence: DbSeq): Unit = {
    /* - - - - Setup temp files that will hold the data prior to database upload - - - - */
    val prefix = sequence.getUUID.toString
    val outputFastaPath = new File(fastaDirectory, s"$prefix.output.fasta")

    if (processed.incrementAndGet() % counterDisplayRate == 0) {
      logger.info(s"Found reference proteome for ${found.get()} " +
        s"(${foundWithSequenceInferred.get()} with inferred sequences) out of ${processed.get()} sequences")
    }

    // Figure out which reference proteomes should be used for this sequence
    val proteomesToQueryAgainst = getMatchingProteomes(sequence)
    if (proteomesToQueryAgainst.isEmpty) return
    found.incrementAndGet()

    // Create the FASTA file out of the database sequence
    writeFastaFileForEachDocument(database, outputFastaPath)(sequence.getUUID.toLong)

    /* - - - - Use Phmmer to find matching/close sequences - - - - */
    var hmmResponse: Option[List[String]] = None
    def writeFastaToStdin(out: OutputStream) {
      val output = createUberProteome(proteomesToQueryAgainst.get)
      output.foreach(x => out.write(x.getBytes))
      out.close()
    }

    def readHmmResultOutput(in: InputStream): Unit = {
      hmmResponse = Some(IOUtils.toString(in, "UTF-8").split("\n").toList)
      in.close()
    }
    def logErrors(err: InputStream) {
      val error = IOUtils.toString(err, "UTF-8")
      if (error.nonEmpty) {
        logger.error(error)
      }
      err.close()
    }
    // We grab the exit code to ensure that the process has completed before deleting the file.
    List(HmmerWrapper.HmmCommands.Phmmer.getCommand, outputFastaPath.getAbsolutePath, "-").
      run(new ProcessIO(writeFastaToStdin, readHmmResultOutput, logErrors)).
      exitValue()

    FileUtils.deleteQuietly(outputFastaPath)

    /* - - - - Take the HMM result and parse the source sequence file for the sequence - - - - */
    val sourceSequenceLinks = getRelevantSequencesFromPhmmerStdout(hmmResponse.get, proteomeLocation)
    val resultingSequences = parseResultSequences(sourceSequenceLinks)

    /* - - - - Do the update to the database - - - - */
    val mongoDatabaseConnection = connectToMongoDatabase(database)
    val metadata = sequence.getMetadata
    val inferArray = new JSONArray()
    val jsons = resultingSequences.asJavaCollection.map(x => x.asJson)
    jsons.foreach(inferArray.put)


    if (resultingSequences.nonEmpty) {
      logger.debug(s"Finished inferring additional sequences for sequence ${sequence.getUUID}")
      foundWithSequenceInferred.incrementAndGet()
    } else {
      // No inferred seq no need to update.
      return
    }

    metadata.put("inferred_sequences", inferArray)
    sequence.setMetadata(metadata)

    mongoDatabaseConnection.updateMetadata(sequence)
  }

  private def getMatchingProteomes(sequence: DbSeq): Option[List[File]] = {
    val organismName = sequence.getOrgName
    val genus = organismName.split(" ")(0)

    // We first check if the exact organism is a match, if it is we return any relevant proteomes
    if (orgsToProteomes.contains(organismName)) {
      orgsToProteomes.get(organismName)
      // Sometimes an exact reference won't be found, so we rely on the genus,
      // a less specific degree of similarity, to find reference proteomes for organisms without a direct match
    } else if (orgsToProteomes.contains(genus)) {
      orgsToProteomes.get(genus)
    } else {
      // Select the correct file to search based on the organism
      // TODO Handle when we don't have a matching proteome
      None
    }
  }

  private def createUberProteome(files: List[File]): Iterator[String] ={
    /*
    Sometimes, there might be matching organism proteomes
    (Multiple strains or genus-level matching), so we make one file containing all of them
    */
    val lines = files.toIterator.flatMap(f => {
        val fileMap = scala.io.Source.fromFile(f).getLines.map(l => {
          // Fasta headers start with '>', indicating a new sequence is being shown.
          val currentLine = if (l.startsWith(">")) {
            // Keep a reference to the FASTA file that this sequence is in by referencing it in the fasta header
            s">${f.getName} | ${l.replaceFirst(">", "")}\n"
          } else {
            s"$l\n"
          }
          currentLine
        })
        fileMap
    })
    lines
  }

  private def getRelevantSequencesFromPhmmerStdout(lines: List[String], proteomeLocation: File): List[SequenceConnection] = {
    val parsedFile = HmmResultParser.parseFile(lines.toIterator).toList
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
      val lines: Iterator[String] = originFile.getLines()

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
