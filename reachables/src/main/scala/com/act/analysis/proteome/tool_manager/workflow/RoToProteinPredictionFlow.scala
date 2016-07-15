package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import com.act.analysis.proteome.files.HmmResultParser
import com.act.analysis.proteome.tool_manager.jobs.{HeaderJob, Job, JobManager}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper}
import com.act.analysis.proteome.tool_manager.workflow_utilities.{MongoWorkflowUtilities, ScalaJobUtilities}
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.io.FastaWriterHelper

import scala.collection.JavaConverters._
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
    val workingDirectory = if (cl.hasOption(WORKING_DIRECTORY_ARG)) cl.getOptionValue(WORKING_DIRECTORY_ARG) else null

    def defineFilePath(optionName: String, identifier: String, defaultValue: String): String = {
      // Spaces tend to be bad for file names
      val filteredIdentifier = identifier.replace(" ", "_")
      // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
      val fileNameHead = s"${if (cl.hasOption(optionName)) cl.getOptionValue(optionName) else defaultValue}"
      val fileName = s"${fileNameHead}_$filteredIdentifier"

      new File(workingDirectory, fileName).getAbsolutePath
    }

    // Header job allows us to have multiple start jobs all line up with this one.
    val head = new HeaderJob()

    // Making into a list will make it so that we just send the whole package to one job.
    // Keeping as individual options will cause individual runs.
    val ro_args = cl.getOptionValues(RO_ARG).toList
    val roContexts = if (cl.hasOption(SET_UNION_ARG)) ro_args.asInstanceOf[List[String]] else List(ro_args)

    val resultFilesBuffer = ListBuffer[String]()
    val contextBatchBuffer = ListBuffer[Job]()

    for (roContext <- roContexts) {
      val outputFastaPath = defineFilePath(OUTPUT_FASTA_FROM_ROS_ARG, roContext.toString, "output.fasta")
      val alignedFastaPath = defineFilePath(ALIGNED_FASTA_FILE_OUTPUT_ARG, roContext.toString, "output.aligned.fasta")
      val outputHmmPath = defineFilePath(OUTPUT_HMM_ARG, roContext.toString, "output.hmm")
      val resultFilePath = defineFilePath(RESULT_FILE_ARG, roContext.toString, "output.hmm.result")

      // For usre later by set compare if option is set.
      resultFilesBuffer.append(resultFilePath)

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

      contextBatchBuffer.append(roToFasta)
    }

    head.thenRunBatch(contextBatchBuffer.toList)

    // Run set union compare if doing set union
    if (cl.hasOption(SET_UNION_ARG)) {
      // Context = All result files
      val setUnionCompareContext = Map(RESULT_FILE_ARG -> resultFilesBuffer.toList)
      val setUnionCompare = ScalaJobWrapper.wrapScalaFunction(setUnionCompareOfHmmerSearchResults, setUnionCompareContext)
      head.thenRun(setUnionCompare)
    }

    head
  }

  def writeFastaFileFromEnzymesMatchingRos(context: Map[String, Any]): Unit = {
    /*
     Commonly used keywords for this mongo query
     */
    val ECNUM = "ecnum"
    val SEQ = "seq"
    val METADATA = "metadata"
    val NAME = "name"
    val ID = "_id"
    val RXN = "rxn"
    val RXN_TO_REACTANTS = "rxn_to_reactants"
    val MECHANISTIC_VALIDATOR = "mechanistic_validator_result"


    val mongoConnection = MongoWorkflowUtilities.connectToDatabase()



    /*
      Query Database for Reaction IDs based on a given RO
     */

    // Map RO values to a list of mechanistic validator things we will want to see
    val roValues = ScalaJobUtilities.AnyStringToList(context(RO_ARG))
    val roObjects = roValues.map(x =>
      new BasicDBObject(s"$MECHANISTIC_VALIDATOR.$x", MongoWorkflowUtilities.EXISTS))
    val queryRoValue = MongoWorkflowUtilities.toDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = MongoWorkflowUtilities.defineOr(queryRoValue)
    val reactionIdReturnFilter = new BasicDBObject(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    JobManager.logInfo(s"Querying reactionIds from Mongo")
    val dbReactionIds = MongoWorkflowUtilities.mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(ID))

    reactionIds.size match {
      // Exit if there are no reactionIds matching the RO
      case n if n < 1 =>
        JobManager.logError("No Reaction IDs found matching any of the ROs supplied")
        throw new Exception("No reaction IDs found.")
      case default =>
        JobManager.logInfo(s"Found $default Reaction IDs matching the RO.")
    }



    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID
     */

    // Structure of query = (Elemmatch (OR -> [RxnIds]))
    val reactions = reactionIds.map(new BasicDBObject(RXN, _)).toList
    val reactionsList = MongoWorkflowUtilities.toDbList(reactions)

    // Look for all elements that match at least one (Enzymes can have multiple reactions)
    val elemMatch = new BasicDBObject(MongoWorkflowUtilities.ELEMMATCH, MongoWorkflowUtilities.defineOr(reactionsList))

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = new BasicDBObject(RXN_TO_REACTANTS, elemMatch)

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val seqFilter = new BasicDBObject
    seqFilter.put(ID, 1)
    seqFilter.put(SEQ, 1)
    seqFilter.put(ECNUM, 1)
    seqFilter.put(s"$METADATA.$NAME", 1)

    JobManager.logInfo("Querying enzymes with the desired reactions for sequences from Mongo")
    val sequenceReturn = MongoWorkflowUtilities.mongoQuerySequences(mongoConnection, seqKey, seqFilter).toList
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

        // Enzymes may not have a name
        val metadataObject: DBObject = x.get(METADATA).asInstanceOf[DBObject]
        val name = if (metadataObject.get(NAME) != null) metadataObject.get(NAME) else "None"

        /*
        These headers are required to be unique or else downstream software will likely crash.
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

    /*
      Write to output
     */
    val outputFasta = context(OUTPUT_FASTA_FROM_ROS_ARG).toString
    JobManager.logInfo(s"Writing ${sequenceReturn.length} sequences to Fasta file at $outputFasta.")
    FastaWriterHelper.writeProteinSequence(new File(outputFasta),
      proteinSequences.asJavaCollection)
  }

  def setUnionCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.union(set)
    }

    JobManager.logInfo(movingSet.toString)
  }

  private def createSetFromHmmerResults(context: Map[String, Any]): List[Set[String]] = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val resultFiles = context(RESULT_FILE_ARG).asInstanceOf[List[String]]

    // Create list of sets
    val fileList = resultFiles.map(HmmResultParser.parseFile)
    fileList.map(x => x.map(y => y(HmmResultParser.HmmResultLine.SEQUENCE_NAME)).toSet)
  }

  def setDisjointCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.diff(set)
    }

    JobManager.logInfo(movingSet.toString)
  }
}
