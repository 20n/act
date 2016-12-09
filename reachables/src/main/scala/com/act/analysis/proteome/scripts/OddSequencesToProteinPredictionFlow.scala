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

  val HELP_MESSAGE = "Workflow to convert odd sequences (Less than 80 AA, don't start with M) into protein predictions based on HMMs."
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  HELP_FORMATTER.setWidth(100)
  val logger = LogManager.getLogger(getClass.getName)

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_COMPARE_PROTEOME_LOCATION = "l"
  private val OPTION_DATABASE = "d"


  def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(OPTION_COMPARE_PROTEOME_LOCATION).
        longOpt("proteome-location").
        hasArg.
        desc("The file path of the proteome file that the constructed HMM should be searched against").
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

  def exitWithHelp(opts: Options): Unit = {
    HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
    System.exit(1)
  }

  def main(args: Array[String]): Unit ={
    defineWorkflow(parseCommandLineOptions(args))
  }

  def defineWorkflow(cl: CommandLine): Unit = {
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)

    val proteomeLocation = new File(cl.getOptionValue(OPTION_COMPARE_PROTEOME_LOCATION))
    if (!proteomeLocation.exists()) throw new FileNotFoundException(s"Proteome location of ${proteomeLocation.getAbsolutePath} does not exist.")

    val database = cl.getOptionValue(OPTION_DATABASE)

    val organismProteomes = proteomeLocation.listFiles().toList
    val orgsToProteomes: Map[String, List[File]] = classifyOrganismByProteome(organismProteomes)

    val oddCriteria = "this.seq.length < 80 || this.seq[0] != 'M'"
    logger.info(s"Defining sequences that match odd criteria of $oddCriteria")
    val matchingSequences: List[DbSeq] = getIdsForEachDocumentInConditional(database)(oddCriteria).toList

    val idToOrganism: Map[Long, String] = matchingSequences.map(doc => {
      val id = doc.getUUID.toLong
      val org = doc.getOrgName
      (id, org)
    }).toMap

    val oddIds = idToOrganism.keys.toList

    logger.info(s"Found ${oddIds.length} ids that match criteria.")

    val fastaDirectory = new File(workingDir, "fastaFiles")
    if (!fastaDirectory.exists()) fastaDirectory.mkdirs()

    val resultHmmDirectory = new File(workingDir, "resultFiles")
    if (!resultHmmDirectory.exists()) resultHmmDirectory.mkdirs()

    val tempSeqDbDir = new File(workingDir, "tmpSeqDb")
    if (!tempSeqDbDir.exists()) tempSeqDbDir.mkdirs()


    val found = new AtomicInteger()
    val processed = new AtomicInteger()
    def defineSequenceBlast(sequence: DbSeq): Unit = {
      // TODO This function currently uses flat files to communicate to CLI, instead of directly transferring them in
      // via stdio and it also parses a flat file instead of doing parsing on the output stream from stdout which
      // could be possible.  This is likely the slowest part and easiest optimization if needed moving forward.

      val prefix = sequence.getUUID.toString
      val resultFilePath =  new File(resultHmmDirectory, s"$prefix.output.search.result")

//      if (resultFilePath.exists()) return

      if (processed.incrementAndGet() % 100 == 0){
        logger.info(s"Found reference proteome for ${found.get()} out of ${processed.get()} sequences")
      }

      val outputFastaPath =   new File(fastaDirectory, s"$prefix.output.fasta")
      val tempSeqFile = new File(tempSeqDbDir, prefix)

      writeFastaFileForEachDocument(database, outputFastaPath)(sequence.getUUID.toLong)
      // Create the FASTA file out of a sequence

      // Select the correct file to search based on the organism
      // TODO Handle when we don't have a matching proteome
      val organism = idToOrganism(sequence.getUUID.toLong)
      val firstOrgWord = organism.split(" ")(0)
      val proteomesToQueryAgainst: Option[List[File]] = if (orgsToProteomes.contains(organism)){
        orgsToProteomes.get(organism)
      } else if(orgsToProteomes.contains(firstOrgWord)) {
        orgsToProteomes.get(firstOrgWord)
      } else {
        None
      }

      if (proteomesToQueryAgainst.isEmpty) return

      val tempProteomeFile = new File(s"$tempSeqFile.tmp_seq_db")
      val writer = new BufferedWriter(new FileWriter(tempProteomeFile))
        proteomesToQueryAgainst.get.foreach(f => {scala.io.Source.fromFile(f).getLines.foreach(l => {
          val currentLine = if (l.startsWith(">")) {
            // Keep a reference to the FASTA file that this sequence is in by referencing it in the fasta header
            s">${f.getName} | ${l.replace(">", "")}\n"
          } else {
            s"$l\n"
          }
          writer.write(currentLine)
        })
      })
      writer.close()


      List(HmmerWrapper.HmmCommands.Phmmer.getCommand, "-o", resultFilePath.getAbsolutePath, outputFastaPath.getAbsolutePath,
        tempProteomeFile.getAbsolutePath).run().exitValue()
      // We grab the exit code to ensure that the future has completed before deleting the file.
      FileUtils.deleteQuietly(outputFastaPath)
      FileUtils.deleteQuietly(tempProteomeFile)

      // Process the file and update the database
      val connection = connectToMongoDatabase(database)

      val parsedFile = HmmResultParser.parseFile(resultFilePath)
      val inferredSequences: List[SequenceConnection] = parsedFile.map(p =>
        SequenceConnection(
          new File(proteomeLocation,
            p(HmmResultParser.HmmResultLine.SEQUENCE_NAME)),
            // Pipe needs to be a character otherwise it will be processed as regex.
            p(HmmResultParser.HmmResultLine.DESCRIPTION).split('|').tail.mkString("|").trim,
            p(HmmResultParser.HmmResultLine.SCORE_DOMAIN).toDouble,
            p(HmmResultParser.HmmResultLine.SCORE_FULL_SEQUENCE).toDouble
        ))

      // Use the inferred connection information to lookup the original sequence in the original file
      val resultingSequences: List[SequenceEntry] = inferredSequences.map(x => {
        val lines = scala.io.Source.fromFile(x.originFile).getLines()
        val result = lines.span(!_.contains(x.sequenceName))

        // Group 1 has everything prior to the stop parsing indicator
        val header = result._2.next
        val resultProtein = result._2.span(l => !l.startsWith(">"))
        val sequence = resultProtein._1.mkString("")
        SequenceEntry(header, sequence, x.scoreDomain, x.scoreFullSequence, x.originFile)
      })

      val metadata = sequence.getMetadata
      val inferArray = new JSONArray()
      val jsons = resultingSequences.asJavaCollection.map(x => x.asJson)
      jsons.foreach(inferArray.put)

      metadata.put("inferred_sequences", inferArray)
      sequence.setMetadata(metadata)

      connection.updateMetadata(sequence)
      println(sequence.getUUID)

      FileUtils.deleteQuietly(resultFilePath)
      found.incrementAndGet()
    }

    matchingSequences.foreach(defineSequenceBlast)

    FileUtils.deleteDirectory(tempSeqDbDir)
    FileUtils.deleteDirectory(fastaDirectory)
//    FileUtils.deleteDirectory(resultHmmDirectory)
  }

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

  private def classifyOrganismByProteome(files: List[File]): Map[String, List[File]] = {
    /*
      >sp|Q0EAB6|5HT1A_HORSE 5-hydroxytryptamine receptor 1A OS=Equus caballus GN=HTR1A PE=2 SV=1
      >                                                         ^             ^
      >                                                          -------------
      >                                                         String of interest
     */
    val fileMap = files.map(f => {
      val firstLine = scala.io.Source.fromFile(f).getLines().next()

      // Take everything after the "OS"
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
}
