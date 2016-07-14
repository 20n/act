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

  override def parseArgs(args: List[String]): Map[String, Option[List[String]]] = {
    // Define what args should be mapped to
    val argMap = mutable.HashMap[String, Option[List[String]]](
      RO_ARG -> None,
      OUTPUT_FASTA_FROM_ROS_ARG -> None,
      ALIGNED_FASTA_FILE_OUTPUT_ARG -> None,
      OUTPUT_HMM_ARG -> None,
      RESULT_FILE_ARG -> None
    )

    // Create and build options
    val options = List[CliOption.Builder](
      CliOption.builder(RO_ARG_PREFIX).required(true).hasArgs.valueSeparator(' ').
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
      case e: ParseException =>
        JobManager.logError(s"Argument parsing failed: ${e.getMessage}\n")
        HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
        System.exit(1)
    }

    // If we parsed options, we check for help and otherwise map the command line args to values
    if (cl.isDefined) {
      val clg = cl.get
      if (clg.hasOption("help")) {
        HELP_FORMATTER.printHelp(this.getClass.getCanonicalName, HELP_MESSAGE, opts, null, true)
        System.exit(1)
      }

      // Setup args for later use
      for (key <- argMap.keysIterator) {
        if (key.equals(RO_ARG)) {
          argMap.put(key, Option(clg.getOptionValues(key).toList))
        } else
          argMap.put(key, Option(List(clg.getOptionValue(key))))
      }
    }

    argMap.toMap
  }

  def defineWorkflow(context: Map[String, Option[List[String]]]): Job = {
    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation("/Volumes/shared-data/Michael/SharedThirdPartyFiles/clustal-omega-1.2.0-macosx")
    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    val roToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos, context)

    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(context(OUTPUT_FASTA_FROM_ROS_ARG).get.head,
      context(ALIGNED_FASTA_FILE_OUTPUT_ARG).get.head)
    alignFastaSequences.writeOutputStreamToLogger()
    alignFastaSequences.writeErrorStreamToLogger()

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(context(OUTPUT_HMM_ARG).get.head,
      context(ALIGNED_FASTA_FILE_OUTPUT_ARG).get.head)
    buildHmmFromFasta.writeErrorStreamToLogger()
    buildHmmFromFasta.writeOutputStreamToLogger()

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(context(OUTPUT_HMM_ARG).get.head,
      panProteomeLocation,
      context(RESULT_FILE_ARG).get.head)
    searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
    searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()

    // Setup ordering
    roToFasta.thenRun(alignFastaSequences).thenRun(buildHmmFromFasta).thenRun(searchNewHmmAgainstPanProteome)

    roToFasta
  }

  def writeFastaFileFromEnzymesMatchingRos(context: Map[String, Option[List[String]]]): Unit = {
    JobManager.logInfo("Setting up Mongo database connection")

    // Instantiate Mongo host.
    val host = "localhost"
    val port = 27017
    val db = "marvin"
    val mongo = new MongoDB(host, port, db)

    // Commonly used keywords for this mongo query
    val ECNUM = "ecnum"
    val SEQ = "seq"
    val METADATA = "metadata"
    val NAME = "name"
    val ID = "_id"

    // Commonly used operators for this mongo query
    val OR = "$or"
    val EXISTS = "$exists"


    /*
    Query Database for Reaction IDs based on a given RO
    */

    // Setup the subparts that will be used in the reaction query
    val queryRoValue = new BasicDBList
    val exists = new BasicDBObject
    exists.put(EXISTS, 1)

    // Map all the ROs to a list which can then be queried against
    for (ro <- context(RO_ARG).get) {
      val mechanisticCheck = new BasicDBObject
      mechanisticCheck.put(s"mechanistic_validator_result.$ro", exists)
      queryRoValue.add(mechanisticCheck)
    }

    // OR <RoValue1, RoValue2 ... etc.>
    val reactionIdQuery = new BasicDBObject
    reactionIdQuery.put(OR, queryRoValue)

    // Just give the reaction ID back
    val reactionIdReturnFilter = new BasicDBObject
    reactionIdReturnFilter.put(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    JobManager.logInfo(s"Querying reactionIds from Mongo")
    val dbReactionIds = mongoQueryReactions(mongo, reactionIdQuery, reactionIdReturnFilter)
    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(ID))

    reactionIds.size match {
      // Exit if there are no reactionIds matching the RO
      case n if n < 1 =>
        JobManager.logError("No Reaction IDs found matching any of the ROs supplied")
        System.exit(1)

      case default =>
        JobManager.logInfo(s"Found $default Reaction IDs matching the RO.")
    }


    /*
    Query sequence database for enzyme sequences by looking for enzymes that have an rID
    */

    // Put all reaction Ids into a list of form [{rxn: id1}, {rxn: id2}]
    val reactionIdsList = new BasicDBList
    for (rId <- reactionIds) {
      val rxnMapping = new BasicDBObject
      rxnMapping.put("rxn", rId)
      reactionIdsList.add(rxnMapping)
    }

    // Or all of the reaction ids so a query that matches any of them is true
    val or = new BasicDBObject
    or.put(OR, reactionIdsList)

    // Look for all elements that match at least one (Enzymes can have multiple reactions)
    val elemMatch = new BasicDBObject
    elemMatch.put("$elemMatch", or)

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = new BasicDBObject
    seqKey.put("rxn_to_reactants", elemMatch)

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val seqFilter = new BasicDBObject
    seqFilter.put(ID, 1)
    seqFilter.put(SEQ, 1)
    seqFilter.put(ECNUM, 1)
    seqFilter.put(s"$METADATA.$NAME", 1)

    JobManager.logInfo("Querying enzymes with the desired reactions for sequences from Mongo")
    val sequenceReturn = mongoQuerySequences(mongo, seqKey, seqFilter).toList
    JobManager.logInfo("Finished sequence query.")

    /*
     Map sequences and name to proteinSequences
     */
    val sequences = sequenceReturn.map(x => {
      // Used for FASTA header
      val seq = x.get(SEQ)

      // Enzymes may not have an enzyme number
      val num = if (x.get(ECNUM) != null) x.get(ECNUM) else "None Available"
      val id = x.get(ID)

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence
        val newSeq = new ProteinSequence(seq.toString)

        // Give a header
        val metadataObject: DBObject = x.get(METADATA).asInstanceOf[DBObject]
        val name = if (metadataObject.get(NAME) != null) metadataObject.get(NAME) else "None Available"

        newSeq.setOriginalHeader(s"NAME: ${name.toString} | EC: ${num.toString} | DB_ID: ${id.toString}")

        Some(newSeq)
      } else {
          None
        }
    })

    // Remove all without sequences
    val proteinSequences = sequences.flatten

    // Write to output
    JobManager.logInfo(s"Writing ${sequenceReturn.length} " +
      s"sequences to Fasta file at ${context(OUTPUT_FASTA_FROM_ROS_ARG).get.head}.")
    FastaWriterHelper.writeProteinSequence(new File(context(OUTPUT_FASTA_FROM_ROS_ARG).get.head),
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
