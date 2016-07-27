package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

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

class EcnumToProteinPredictionFlow extends Workflow {
  override val HELP_MESSAGE = "Workflow to convert EC numbers into protein predictions based on HMMs."
  private val logger = LogManager.getLogger(getClass.getName)
  private val EC_NUM_ARG_PREFIX = "e"
  private val OUTPUT_FASTA_FROM_ECNUM_ARG_PREFIX = "f"
  private val ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX = "a"
  private val OUTPUT_HMM_ARG_PREFIX = "m"
  private val RESULT_FILE_ARG_PREFIX = "o"
  private val WORKING_DIRECTORY_ARG_PREFIX = "w"
  private val CLUSTAL_BINARIES_ARG_PREFIX = "c"

  private val SET_LOCATION = "setLocation"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(EC_NUM_ARG_PREFIX).
        required(true).
        hasArg.
        longOpt("ec-number")
        .desc("EC number to query against in format of X.X.X.X, " +
          "if you do not enter a fully defined sequence of four, " +
          "it will assume you want all reactions within a subgroup, " +
          "such as the value 6.1.1 will match 6.1.1.1 as well as 6.1.1.2"),

      CliOption.builder(OUTPUT_FASTA_FROM_ECNUM_ARG_PREFIX).
        hasArg.
        longOpt("output-fasta-from-ecnum-location").
        desc(s"Output FASTA sequence containing all the enzyme sequences that catalyze a reaction within the ecnum."),


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

    // Grab the ec number
    val ec_num = cl.getOptionValue(EC_NUM_ARG_PREFIX)


    // Setup all the constant paths here
    val outputFastaPath = defineFilePath(
      OUTPUT_FASTA_FROM_ECNUM_ARG_PREFIX,
      ec_num,
      "output.fasta"
    )

    val alignedFastaPath = defineFilePath(
      ALIGNED_FASTA_FILE_OUTPUT_ARG_PREFIX,
      ec_num,
      "output.aligned.fasta"
    )

    val outputHmmPath = defineFilePath(
      OUTPUT_HMM_ARG_PREFIX,
      ec_num,
      "output.hmm"
    )

    val resultFilePath = defineFilePath(
      RESULT_FILE_ARG_PREFIX,
      ec_num,
      "output.hmm.result"
    )

    // Create the FASTA file out of all the relevant sequences.
    val ecNumberToFastaContext = Map(
      EC_NUM_ARG_PREFIX -> ec_num,
      OUTPUT_FASTA_FROM_ECNUM_ARG_PREFIX -> outputFastaPath
    )
    val ecNumberToFasta =
      ScalaJobWrapper.wrapScalaFunction(writeFastaFileFromEnzymesMatchingEcnums, ecNumberToFastaContext)
    head.thenRun(ecNumberToFasta)

    // Align Fasta sequence
    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaPath, alignedFastaPath)
    alignFastaSequences.writeOutputStreamToLogger()
    alignFastaSequences.writeErrorStreamToLogger()
    head.thenRun(alignFastaSequences)

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmPath, alignedFastaPath)
    buildHmmFromFasta.writeErrorStreamToLogger()
    buildHmmFromFasta.writeOutputStreamToLogger()
    head.thenRun(buildHmmFromFasta)

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmPath, panProteomeLocation, resultFilePath)
    searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
    searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()
    head.thenRun(searchNewHmmAgainstPanProteome)

    head
  }

  /**
    * Takes in a ecnum and translates them into FASTA files with all the enzymes that do that Ecnum
    *
    * @param context Where the above information is gotten
    */
  def writeFastaFileFromEnzymesMatchingEcnums(context: Map[String, Any]): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingEcnums")
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
      Query Database for Reaction IDs based on a given EC Number

      EC Numbers are formatted at X.X.X.X so we use a regex match of

      ^6\.1\.1\.1$
     */

    val roughEcnum = context.get(EC_NUM_ARG_PREFIX).get.toString
    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = MongoWorkflowUtilities.defineRegex(ecnumRegex)
    val reactionIdQuery = new BasicDBObject(ECNUM, regex)
    val reactionIdReturnFilter = new BasicDBObject(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      MongoWorkflowUtilities.mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionIds = MongoWorkflowUtilities.dbIteratorToSet(dbReactionIdsIterator)

    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(ID))

    // Exit if there are no reactionIds matching the Ecnum
    reactionIds.size match {
      case n if n < 1 =>
        methodLogger.error("No Reaction IDs found matching any of the Ecnum supplied")
        throw new Exception(s"No reaction IDs found for the given Ecnum.")
      case default =>
        methodLogger.info(s"Found $default Reaction IDs matching the Ecnum.")
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
      s"Original query was for $roughEcnum")
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
    val outputFasta = context(OUTPUT_FASTA_FROM_ECNUM_ARG_PREFIX).toString
    if (proteinSequences.length < 1) {
      methodLogger.error("No sequences found after filtering for values with no sequences")
    } else {
      methodLogger.info(s"Writing ${proteinSequences.length} sequences to Fasta file at $outputFasta.")
    }
    FastaWriterHelper.writeProteinSequence(new File(outputFasta),
      proteinSequences.asJavaCollection)
  }

  /**
    * Input is a value of form #.#.#.# where the value can stop at any #
    *
    * Valid inputs would therefore be 1, 1.2, 1.2.3, 1.2.3.4
    *
    * Invalid inputs would be 1., 1.2.3.4.5
    *
    * @param ecnum
    *
    * @return
    */
  def formatEcNumberAsRegex(ecnum: String): String = {
    val anyNumberOfNumbers = "[0-9]*"
    val basicRegex = ListBuffer(anyNumberOfNumbers, anyNumberOfNumbers, anyNumberOfNumbers, anyNumberOfNumbers)

    val dividedInput = ecnum.split('.')

    for (i <- dividedInput.indices) {
      basicRegex(i) = dividedInput(i)
    }

    /*
      ^ is the start of the string, $ is the end of the string.

      We use a \\. seperator so that we match periods (Periods must be escaped in regex).
    */
    "^" + basicRegex.mkString(sep = "\\.") + "$"
  }
}
