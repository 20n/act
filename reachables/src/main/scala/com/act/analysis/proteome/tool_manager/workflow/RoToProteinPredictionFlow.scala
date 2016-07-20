package com.act.analysis.proteome.tool_manager.workflow

import java.io.{File, FileWriter}

import com.act.analysis.proteome.files.HmmResultParser
import com.act.analysis.proteome.tool_manager.jobs.{HeaderJob, Job}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.analysis.proteome.tool_manager.workflow_utilities.MongoWorkflowUtilities
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.io.FastaWriterHelper

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer


class RoToProteinPredictionFlow extends Workflow {
  override val HELP_MESSAGE = "Workflow to convert RO numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)
  private val RO_ARG_PREFIX = "r"
  private val OUTPUT_FASTA_FROM_ROS_ARG_PREFIX = "f"
  private val ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX = "a"
  private val OUTPUT_HMM_ARG_PREFIX = "m"
  private val RESULT_FILE_ARG_PREFIX = "o"
  private val WORKING_DIRECTORY_ARG_PREFIX = "w"
  private val SET_UNION_ARG_PREFIX = "u"
  private val SET_INTERSECTION_ARG_PREFIX = "i"
  private val CLUSTAL_BINARIES_ARG_PREFIX = "cb"

  private val SET_LOCATION = "setLocation"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(RO_ARG_PREFIX).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro-values").desc("RO number that should be querying against."),

      CliOption.builder(OUTPUT_FASTA_FROM_ROS_ARG_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ros-location").
        desc(s"Output FASTA sequence containing all the enzyme sequences that catalyze a reaction within the RO."),


      CliOption.builder(ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX).
        hasArg.
        longOpt("aligned-fasta-file-output-location").
        desc(s"Output FASTA file after being aligned."),

      CliOption.builder(OUTPUT_HMM_ARG_PREFIX).
        hasArg.
        longOpt("output-hmm-profile-location").
        desc(s"Output HMM profile produced from the aligned FASTA."),

      CliOption.builder(RESULT_FILE_ARG_PREFIX).
        hasArg.
        longOpt("results-file-location").
        desc(s"Output HMM search on pan proteome with the produced HMM"),

      CliOption.builder(WORKING_DIRECTORY_ARG_PREFIX).
        hasArg.
        longOpt("working-directory").
        desc("Run and create all files from a working directory you designate."),

      CliOption.builder(SET_UNION_ARG_PREFIX).
        longOpt("obtain-set-union-results").
        desc("Run all ROs as individual runs, and then do a set union on all the output proteins."),

      CliOption.builder(SET_INTERSECTION_ARG_PREFIX).
        longOpt("obtain-set-intersection-results").
        desc("Run all ROs as individual runs, then do a set intersection on all the output proteins."),

      CliOption.builder(CLUSTAL_BINARIES_ARG_PREFIX).
        longOpt("clustal-omega-binary-location").
        hasArg.
        desc("Set the location of where the ClustalOmega binaries are located at").
        required(true),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  def defineWorkflow(cl: CommandLine): Job = {
    logger.info("Finished processing command line information")
    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation(cl.getOptionValue(CLUSTAL_BINARIES_ARG_PREFIX))

    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    // Setup file pathing
    val workingDirectory = cl.getOptionValue(WORKING_DIRECTORY_ARG_PREFIX, null)

    def defineFilePath(optionName: String, identifier: String, defaultValue: String): String = {
      // Spaces tend to be bad for file names
      val filteredIdentifier = identifier.replace(" ", "_")

      // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
      val fileNameHead = cl.getOptionValue(optionName, defaultValue)
      val fileName = s"${fileNameHead}_$filteredIdentifier"

      new File(workingDirectory, fileName).getAbsolutePath
    }

    // Header job allows us to have multiple start jobs all line up with this one.
    val head = new HeaderJob()

    // Making into a list will make it so that we just send the whole package to one job.
    // Keeping as individual options will cause individual runs.
    val ro_args = cl.getOptionValues(RO_ARG_PREFIX).toList
    val setQuery = cl.hasOption(SET_UNION_ARG_PREFIX) | cl.hasOption(SET_INTERSECTION_ARG_PREFIX)

    /*
     This RO context actually takes two types, either a List[String]
     if we are processing a list of single RO values, or a List[List[String]] to keep the API consistent.
     Then, it just iterates over only a single roContext in roContexts, passing the List[String] as the entire context
      */
    val roContexts = if (setQuery) ro_args.asInstanceOf[List[String]] else List(ro_args.asInstanceOf[List[String]])

    // For use later by set compare if option is set.
    val resultFilesBuffer = ListBuffer[String]()

    for (roContext <- roContexts) {
      // Setup all the constant paths here
      val outputFastaPath = defineFilePath(
        OUTPUT_FASTA_FROM_ROS_ARG_PREFIX,
        roContext.toString,
        "output.fasta"
      )

      val alignedFastaPath = defineFilePath(
        ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX,
        roContext.toString,
        "output.aligned.fasta"
      )

      val outputHmmPath = defineFilePath(
        OUTPUT_HMM_ARG_PREFIX,
        roContext.toString,
        "output.hmm"
      )

      val resultFilePath = defineFilePath(
        RESULT_FILE_ARG_PREFIX,
        roContext.toString,
        "output.hmm.result"
      )

      resultFilesBuffer.append(resultFilePath)

      // Create the FASTA file out of all the relevant sequences.
      val roToFastaContext = Map(
        RO_ARG_PREFIX -> roContext,
        OUTPUT_FASTA_FROM_ROS_ARG_PREFIX -> outputFastaPath
      )
      val roToFasta = ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingRos, roToFastaContext)
      head.thenRunAtPosition(roToFasta, 0)

      // Align Fasta sequence
      val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
      alignFastaSequences.writeOutputStreamToLogger()
      alignFastaSequences.writeErrorStreamToLogger()
      head.thenRunAtPosition(alignFastaSequences, 1)

      // Build a new HMM
      val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
      buildHmmFromFasta.writeErrorStreamToLogger()
      buildHmmFromFasta.writeOutputStreamToLogger()
      head.thenRunAtPosition(buildHmmFromFasta, 2)

      // Use the built HMM to find novel proteins
      val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, panProteomeLocation, resultFilePath)
      searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
      searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()
      head.thenRunAtPosition(searchNewHmmAgainstPanProteome, 3)
    }

    // Run set operations
    val setContext = Map(
      RESULT_FILE_ARG_PREFIX -> resultFilesBuffer.toList,
      SET_LOCATION -> new File(RESULT_FILE_ARG_PREFIX).getParent,
      RO_ARG_PREFIX -> ro_args.mkString(sep = "_")
    )

    if (cl.hasOption(SET_UNION_ARG_PREFIX)) {
      // Context = All result files
      val setJob = ScalaJobWrapper.wrapScalaFunction(setUnionCompareOfHmmerSearchResults, setContext)
      head.thenRun(setJob)
    }
    if (cl.hasOption(SET_INTERSECTION_ARG_PREFIX)) {
      val setJob = ScalaJobWrapper.wrapScalaFunction(setIntersectionCompareOfHmmerSearchResults, setContext)
      head.thenRun(setJob)
    }

    head
  }

  /**
    * Takes in a set of ROs and translates them into FASTA files with all the enzymes that do that RO
    *
    * @param context Where the above information is gotten
    */
  def writeFastaFileFromEnzymesMatchingRos(context: Map[String, Any]): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingRos")
    /*
     Commonly used keywords for this mongo query
     */
    val ECNUM = "ecnum"
    val SEQ = "seq"
    val METADATA = "metadata"
    val NAME = "name"
    val ID = "_id"
    val RXN_REFS = "rxn_refs"
    val MECHANISTIC_VALIDATOR = "mechanistic_validator_result"

    val mongoConnection = MongoWorkflowUtilities.connectToDatabase()


    /*
      Query Database for Reaction IDs based on a given RO
     */

    /*
     Map RO values to a list of mechanistic validator things we will want to see

     Can be either List[String] or String
    */
    val roValues: List[String] = context(RO_ARG_PREFIX) match {
      case string if string.isInstanceOf[String] => List(string.asInstanceOf[String])
      case string => string.asInstanceOf[List[String]]
    }


    val roObjects = roValues.map(x =>
      new BasicDBObject(s"$MECHANISTIC_VALIDATOR.$x", MongoWorkflowUtilities.EXISTS))
    val queryRoValue = MongoWorkflowUtilities.toDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = MongoWorkflowUtilities.defineOr(queryRoValue)
    val reactionIdReturnFilter = new BasicDBObject(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      MongoWorkflowUtilities.mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionIds = MongoWorkflowUtilities.dbIteratorToSet(dbReactionIdsIterator)
    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(ID))

    // Exit if there are no reactionIds matching the RO
    reactionIds.size match {
      case n if n < 1 =>
        methodLogger.error("No Reaction IDs found matching any of the ROs supplied")
        throw new Exception(s"No reaction IDs found for the given RO.")
      case default =>
        methodLogger.info(s"Found $default Reaction IDs matching the RO.")
    }



    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID
     */
    // Structure of query = (Elemmatch (OR -> [RxnIds]))
    val reactionList = new BasicDBList
    reactionIds.map(reactionList.add)

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = new BasicDBObject(RXN_REFS, MongoWorkflowUtilities.defineIn(reactionList))

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val seqFilter = new BasicDBObject
    seqFilter.put(ID, 1)
    seqFilter.put(SEQ, 1)
    seqFilter.put(ECNUM, 1)
    seqFilter.put(s"$METADATA.$NAME", 1)

    methodLogger.info("Querying enzymes with the desired reactions for sequences from Mongo")
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $seqFilter. " +
      s"Original query was for $roValues")
    val sequenceReturnIterator: Iterator[DBObject] =
      MongoWorkflowUtilities.mongoQuerySequences(mongoConnection, seqKey, seqFilter)
    methodLogger.info("Finished sequence query.")



    /*
      Map sequences and name to proteinSequences
     */
    val proteinSequences: ListBuffer[ProteinSequence] = new ListBuffer[ProteinSequence]
    for (sequence: DBObject <- sequenceReturnIterator) {
      val seq = sequence.get(SEQ)
      // Enzymes may not have an enzyme number
      val num = if (sequence.get(ECNUM) != null) sequence.get(ECNUM) else "None"
      val id = sequence.get(ID)

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence so that we can use the FASTA file generator they provide.
        val newSeq = new ProteinSequence(seq.toString)

        // Enzymes may not have a name
        val metadataObject: DBObject = sequence.get(METADATA).asInstanceOf[DBObject]
        val name = if (metadataObject.get(NAME) != null) metadataObject.get(NAME) else "None"

        /*
        These headers are required to be unique or else downstream software will likely crash.
        This header may not be unique based on Name/EC number alone (For example, if they are both none),
        but the DB_ID should guarantee uniqueness
        */
        newSeq.setOriginalHeader(s"NAME: ${name.toString} | EC: ${num.toString} | DB_ID: ${id.toString}")
        proteinSequences.append(newSeq)
      }
    }

    /*
     Write to output
    */
    val outputFasta = context(OUTPUT_FASTA_FROM_ROS_ARG_PREFIX).toString
    if (proteinSequences.length < 1) {
      methodLogger.error("No sequences found after filtering for values with no sequences")
    } else {
      methodLogger.info(s"Writing ${proteinSequences.length} sequences to Fasta file at $outputFasta.")
    }
    FastaWriterHelper.writeProteinSequence(new File(outputFasta),
      proteinSequences.asJavaCollection)
  }

  /**
    * On a list of hmmer result files, creates a file containing all the proteins available in those files.
    *
    * @param context Passes which RO arg and the location that the set should be stored at.
    */
  def setUnionCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.union(set)
    }

    saveSet(new File(context(SET_LOCATION).asInstanceOf[String], s"${context(RO_ARG_PREFIX)}.union.set"), movingSet)
  }

  /**
    * On a list of hmmer result files,
    * creates a file containing the intersection between all the proteins in those files.
    *
    * @param context Passes which RO arg and the location that the set should be stored at.
    */
  def setIntersectionCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.intersect(set)
    }
    saveSet(new File(
      context(SET_LOCATION).asInstanceOf[String],
      s"${context(RO_ARG_PREFIX)}.intersection.set"),
      movingSet)
  }

  /**
    * Given a set of hmmer files, creates sets from their top-ranked sequences.
    *
    * @param context Passes all the result files.
    *
    * @return
    */
  private def createSetFromHmmerResults(context: Map[String, Any]): List[Set[String]] = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val resultFiles = context(RESULT_FILE_ARG_PREFIX).asInstanceOf[List[String]]

    // Create list of sets
    val fileList = resultFiles.map(HmmResultParser.parseFile)
    fileList.map(x => x.map(y => y(HmmResultParser.HmmResultLine.SEQUENCE_NAME)).toSet)
  }

  /**
    * Sorts and saves the output set to a file
    *
    * @param file Where to save the file
    * @param set  The set which is to be saved
    */
  private def saveSet(file: File, set: Set[String]): Unit = {
    val orderedList = set.toList.sorted
    val writer = new FileWriter(file)
    writer.write("Set compare data file\n")
    writer.write(s"File type: ${file.getName}\n")
    writer.write("Proteins in set:\n")
    for (entry <- orderedList) {
      writer.write(s"$entry\n")
    }
    writer.close()
  }
}
