package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import act.server.MongoDB
import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.commons.cli.{CommandLine, DefaultParser, Options, ParseException, Option => CliOption}
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.io.FastaWriterHelper

import scala.collection.JavaConverters._
import scala.collection.mutable


class RoToProteinPredictionFlow extends Workflow {
  private val RO_ARG = "RoValue"
  private val RO_ARG_PREFIX = "r"

  private val OUTPUT_FASTA_FROM_ROS_ARG = "OutputFastaFromRos"
  private val OUTPUT_FASTA_FROM_ROS_ARG_PREFIX = "f"

  private val ALIGNED_FASTA_FILE_OUTPUT_ARG = "AlignedFastaFileOutput"
  private val ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX = "a"

  private val OUTPUT_HMM_ARG = "OutputHmmProfile"
  private val OUTPUT_HMM_ARG_PREFIX = "m"

  private val RESULT_FILE_ARG = "ResultsFile"
  private val RESULT_FILE_ARG_PREFIX = "o"


  private val HELP_MESSAGE = "Workflow to convert RO numbers into protein predictions based on HMMs."

  var argMap = mutable.HashMap[String, Option[String]](
    RO_ARG -> None,
    OUTPUT_FASTA_FROM_ROS_ARG -> None,
    ALIGNED_FASTA_FILE_OUTPUT_ARG -> None,
    OUTPUT_HMM_ARG -> None,
    RESULT_FILE_ARG -> None
  )

  override def setArgument(argName: String, value: String): Unit = {
    argMap.put(argName, Option(value))
  }

  override def parseArgs(args: List[String]) {
    // Create and build options
    val options = List[CliOption.Builder](
      CliOption.builder(RO_ARG_PREFIX).required(true).hasArg.
        longOpt(RO_ARG).desc("RO number that should be querying against."),

      CliOption.builder(OUTPUT_FASTA_FROM_ROS_ARG_PREFIX).required(true).hasArg.
        longOpt(OUTPUT_FASTA_FROM_ROS_ARG).desc("Output FASTA sequence containing all the enzyme" +
        " sequences that catalyze a reaction within the RO."),

      CliOption.builder(ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX).required(true).hasArg.
        longOpt(ALIGNED_FASTA_FILE_OUTPUT_ARG).desc("Output FASTA file after being aligned"),

      CliOption.builder(OUTPUT_HMM_ARG_PREFIX).required(true).hasArg.
        longOpt(OUTPUT_HMM_ARG).desc("Output HMM profile produced from the aligned FASTA"),

      CliOption.builder(RESULT_FILE_ARG_PREFIX).required(true).hasArg.
        longOpt(RESULT_FILE_ARG).desc("Output HMM search on pan proteome with the produced HMM profile"),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }

    // Parse command line options
    var cl: Option[CommandLine] = None
    try {
      val parser = new DefaultParser()
      cl = Option(parser.parse(opts, args.toArray[String]))
    } catch {
      case e: ParseException => {
        JobManager.logError(s"Argument parsing failed: ${e.getMessage}\n")
        HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
        System.exit(1)
      }
    }

    // If we parsed options, we check for help and otherwise map the command line args to values
    if (cl.isDefined) {
      val clg = cl.get
      if (clg.hasOption("help")) {
        HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
        System.exit(1)
      }

      for (key <- argMap.keysIterator) {
        argMap.put(key, Option(clg.getOptionValue(key)))
      }
    }
  }

  def defineWorkflow(): Job = {
    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation("/Volumes/shared-data/Michael/SharedThirdPartyFiles/clustal-omega-1.2.0-macosx")
    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    val roToFasta = ScalaJobWrapper.wrapScalaFunction(getRosToFastaFromDb)


    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(argMap(OUTPUT_FASTA_FROM_ROS_ARG).get,
      argMap(ALIGNED_FASTA_FILE_OUTPUT_ARG).get)
    alignFastaSequences.writeOutputStreamToLogger()
    alignFastaSequences.writeErrorStreamToLogger()

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(argMap(OUTPUT_HMM_ARG).get, argMap(ALIGNED_FASTA_FILE_OUTPUT_ARG).get)
    buildHmmFromFasta.writeErrorStreamToLogger()
    buildHmmFromFasta.writeOutputStreamToLogger()

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(argMap(OUTPUT_HMM_ARG).get,
      panProteomeLocation,
      argMap(RESULT_FILE_ARG).get)
    searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
    searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()

    // Setup ordering
    roToFasta.thenRun(alignFastaSequences).thenRun(buildHmmFromFasta).thenRun(searchNewHmmAgainstPanProteome)

    roToFasta
  }

  def getRosToFastaFromDb(): Unit = {
    // Instantiate Mongo host.
    val host = "localhost"
    val port = 27017
    val db = "marvin"
    val mongo = new MongoDB(host, port, db)

    val ECNUM = "ecnum"
    val SEQ = "seq"
    val METADATA = "metadata"
    val NAME = "name"
    /*
    Query Database for enzyme IDs based on a given RO
     */
    val key = new BasicDBObject
    val exists = new BasicDBObject
    val returnFilter = new BasicDBObject
    exists.put("$exists", "true")
    key.put(s"mechanistic_validator_result.${argMap(RO_ARG).get}", exists)
    returnFilter.put(ECNUM, 1)

    JobManager.logInfo(s"Querying reactionIds from Mongo")
    val reactionIds = mongoQueryReactions(mongo, key, returnFilter).map(x => x.get(ECNUM))
    JobManager.logInfo(s"Found ${reactionIds.size} enzyme ID numbers from RO.")


    /*
    Query sequence database for enzyme sequences
     */
    val seqKey = new BasicDBObject
    val in = new BasicDBObject
    val reactionIdsList = new BasicDBList
    for (rId <- reactionIds) {
      reactionIdsList.add(rId)
    }

    in.put("$in", reactionIdsList)
    seqKey.put(ECNUM, in)
    val seqFilter = new BasicDBObject
    seqFilter.put(SEQ, 1)
    seqFilter.put(ECNUM, 1)
    seqFilter.put(s"$METADATA.$NAME", 1)

    JobManager.logInfo("Querying Enzyme IDs for sequences from Mongo")
    val sequenceReturn = mongoQuerySequences(mongo, seqKey, seqFilter).toList
    JobManager.logInfo("Finished sequence query.")

    // Map sequences and name to proteinSequences

    val sequences = sequenceReturn.map(x => {
      val seq = x.get(SEQ)
      if (seq != null) {
        val newSeq = new ProteinSequence(seq.toString)

        // TODO CLEANUP

        val num = x.get(ECNUM)
        val metadataObject: DBObject = x.get(METADATA).asInstanceOf[DBObject]
        val name = metadataObject.get(NAME)

        if (num != null) {
          newSeq.setOriginalHeader(s"${name.toString} | ${num.toString}")
          Some(newSeq)
        } else {
          None
        }
      } else {
        None
      }
    })

    val proteinSequences = sequences.flatten

    // Write to output
    JobManager.logInfo(s"Writing ${sequenceReturn.length} " +
      s"sequences to Fasta file at ${argMap(OUTPUT_FASTA_FROM_ROS_ARG).get}.")

    FastaWriterHelper.writeProteinSequence(new File(argMap(OUTPUT_FASTA_FROM_ROS_ARG).get),
      proteinSequences.asJavaCollection)
  }

  def mongoQueryReactions(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] = {
    val ret = mongo.getIteratorOverReactions(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }

  def mongoQuerySequences(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] = {
    val ret = mongo.getIteratorOverSeq(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }

}
