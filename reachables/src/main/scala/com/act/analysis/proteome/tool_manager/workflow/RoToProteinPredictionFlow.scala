package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import act.server.MongoDB
import com.act.analysis.proteome.files.HmmResultParser
import com.act.analysis.proteome.tool_manager.jobs.{HeaderJob, Job, JobManager}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.io.FastaWriterHelper

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer


class RoToProteinPredictionFlow extends Workflow {
  override val HELP_MESSAGE = "Workflow to convert RO numbers into protein predictions based on HMMs."
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
  private val WORKING_DIRECTORY_ARG = "WorkingDirectory"
  private val WORKING_DIRECTORY_ARG_PREFIX = "w"
  private val SET_UNION_ARG = "SetUnionResults"
  private val SET_UNION_ARG_PREFIX = "s"


  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(RO_ARG_PREFIX).required(true).hasArgs.valueSeparator(' ').
        longOpt(RO_ARG).desc("RO number that should be querying against."),


      CliOption.builder(OUTPUT_FASTA_FROM_ROS_ARG_PREFIX).hasArg.
        longOpt(OUTPUT_FASTA_FROM_ROS_ARG).desc(s"Output FASTA sequence containing all the enzyme sequences that " +
        s"catalyze a reaction within the RO."),


      CliOption.builder(ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX).hasArg.
        longOpt(ALIGNED_FASTA_FILE_OUTPUT_ARG).desc(s"Output FASTA file after being aligned."),

      CliOption.builder(OUTPUT_HMM_ARG_PREFIX).hasArg.
        longOpt(OUTPUT_HMM_ARG).desc(s"Output HMM profile produced from the aligned FASTA."),

      CliOption.builder(RESULT_FILE_ARG_PREFIX).hasArg.
        longOpt(RESULT_FILE_ARG).desc(s"Output HMM search on pan proteome with the produced HMM"),

      CliOption.builder(WORKING_DIRECTORY_ARG_PREFIX).hasArg.
        longOpt(WORKING_DIRECTORY_ARG).desc("Run and create all files from a working directory you designate."),

      CliOption.builder(SET_UNION_ARG_PREFIX).
        longOpt(SET_UNION_ARG).desc("If to run ROs are individual runs, and then set compare the results."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def defineWorkflow(cl: CommandLine): Job = {
    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation("/Volumes/shared-data/Michael/SharedThirdPartyFiles/clustal-omega-1.2.0-macosx")
    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    // Setup file pathing
    val workDirectory = if (cl.hasOption(WORKING_DIRECTORY_ARG)) cl.getOptionValue(WORKING_DIRECTORY_ARG) else null

    def defineFilePath(optionName: String, identifier: String, defaultValue: String): String = {
      val filteredIdentifier = identifier.replace(" ", "_")
      // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
      val fileName = s"${if (cl.hasOption(optionName)) cl.getOptionValue(optionName) else defaultValue}" +
        s"_$filteredIdentifier"

      new File(workDirectory, fileName).getAbsolutePath
    }

    // Header job allows us to have multiple start jobs all line up with this one.
    val head = new HeaderJob()

    // Making into a list will make it so that we just send the whole package to one job.
    // Keeping as individual options will cause individual runs.
    val ro_args = cl.getOptionValues(RO_ARG).toList
    val roContexts = if (cl.hasOption(SET_UNION_ARG)) ro_args.asInstanceOf[List[String]] else List(ro_args)

    val resultFiles = ListBuffer[String]()
    val contextBatch = ListBuffer[Job]()
    for (roContext <- roContexts) {
      val outputFastaPath = defineFilePath(OUTPUT_FASTA_FROM_ROS_ARG, roContext.toString, "output.fasta")
      val alignedFastaPath = defineFilePath(ALIGNED_FASTA_FILE_OUTPUT_ARG, roContext.toString, "output.aligned.fasta")
      val outputHmmPath = defineFilePath(OUTPUT_HMM_ARG, roContext.toString, "output.hmm")
      val resultFilePath = defineFilePath(RESULT_FILE_ARG, roContext.toString, "output.hmm.result")
      resultFiles.append(resultFilePath)

      // Context RO arg, OutputFasta
      val roToFastaContext = Map(
        RO_ARG -> roContext,
        OUTPUT_FASTA_FROM_ROS_ARG -> outputFastaPath
      )
      val roToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos, roToFastaContext)

      // Align Fasta sequence
      val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
      alignFastaSequences.writeOutputStreamToLogger()
      alignFastaSequences.writeErrorStreamToLogger()

      // Build a new HMM
      val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
      buildHmmFromFasta.writeErrorStreamToLogger()
      buildHmmFromFasta.writeOutputStreamToLogger()

      // Use the built HMM to find novel proteins
      val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, panProteomeLocation, resultFilePath)
      searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
      searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()

      // Setup ordering
      roToFasta.thenRun(alignFastaSequences).
        thenRun(buildHmmFromFasta).thenRun(searchNewHmmAgainstPanProteome)

      contextBatch.append(roToFasta)
    }

    head.thenRunBatch(contextBatch.toList)

    // Run set union compare if doing set union
    if (cl.hasOption(SET_UNION_ARG)) {
      // Context = All result files
      val setUnionCompareContext = Map(RESULT_FILE_ARG -> resultFiles.toList)
      val setUnionCompare = ScalaJobWrapper.wrapScalaFunction(setCompareOfHmmerSearchResults, setUnionCompareContext)
      head.thenRun(setUnionCompare)
    }

    head
  }

  def writeFastaFileFromEnzymesMatchingRos(context: Map[String, Any]): Unit = {
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
    println(context(RO_ARG))

    val roValues = ListBuffer[String]()
    try {
      // Try to cast as string
      roValues.append(context(RO_ARG).asInstanceOf[String])
    } catch {
      case e: ClassCastException =>
        // Assume it is a list of strings
        val contextList: List[String] = context(RO_ARG).asInstanceOf[List[String]]
        for (value <- contextList) {
          roValues.append(value)
        }
    }

    for (ro <- roValues) {
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
      val num = if (x.get(ECNUM) != null) x.get(ECNUM) else "None"
      val id = x.get(ID)

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence so that we can use their FASTA file generator.
        val newSeq = new ProteinSequence(seq.toString)

        val metadataObject: DBObject = x.get(METADATA).asInstanceOf[DBObject]
        val name = if (metadataObject.get(NAME) != null) metadataObject.get(NAME) else "None"

        /*
        These headers are required to be unique.
        This header may not be unique based on Name/EC number alone (For example, if they are both none),
        but the DB_ID should guarantee uniqueness
        */
        newSeq.setOriginalHeader(s"NAME: ${name.toString} | EC: ${num.toString} | DB_ID: ${id.toString}")

        Some(newSeq)
      } else {
          None
        }
    })

    // Remove all without sequences
    val proteinSequences = sequences.flatten

    // Write to output
    val outputFasta = context(OUTPUT_FASTA_FROM_ROS_ARG).toString
    JobManager.logInfo(s"Writing ${sequenceReturn.length} " +
      s"sequences to Fasta file at $outputFasta.")
    FastaWriterHelper.writeProteinSequence(new File(outputFasta),
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

  def setCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val resultFiles = context(RESULT_FILE_ARG).asInstanceOf[List[String]]

    // Create list of sets
    val fileList = resultFiles.map(HmmResultParser.parseFile)
    val setLists = fileList.map(x => x.map(y => y(HmmResultParser.HmmResultLine.SEQUENCE_NAME)).toSet)

    // Sequentially apply sets
    var movingSet = setLists.head
    for (set <- setLists.tail) {
      movingSet = movingSet.union(set)
    }

    JobManager.logInfo(movingSet.toString)
  }
}
