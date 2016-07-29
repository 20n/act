package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite

import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.{MongoWorkflowUtilities, WriteProteinSequencesToFasta}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence

import scala.collection.mutable.ListBuffer

trait RoToSequences extends MongoWorkflowUtilities with WriteProteinSequencesToFasta {
  val OPTION_RO_ARG_PREFIX: String

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

    val mongoConnection = connectToMongoDatabase()


    /*
      Query Database for Reaction IDs based on a given RO
     */

    /*
     Map RO values to a list of mechanistic validator things we will want to see

     Can be either List[String] or String
    */
    val roValues: List[String] = context(OPTION_RO_ARG_PREFIX) match {
      case string if string.isInstanceOf[String] => List(string.asInstanceOf[String])
      case string => string.asInstanceOf[List[String]]
    }


    val roObjects = roValues.map(x =>
      new BasicDBObject(s"$MECHANISTIC_VALIDATOR.$x", getMongoExists))
    val queryRoValue = convertListToMongoDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = defineMongoOr(queryRoValue)
    val reactionIdReturnFilter = new BasicDBObject(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionIds = mongoDbIteratorToSet(dbReactionIdsIterator)
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
    val seqKey = new BasicDBObject(RXN_REFS, defineMongoIn(reactionList))

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val seqFilter = new BasicDBObject
    seqFilter.put(ID, 1)
    seqFilter.put(SEQ, 1)
    seqFilter.put(ECNUM, 1)
    seqFilter.put(s"$METADATA.$NAME", 1)

    methodLogger.info("Querying enzymes with the desired reactions for sequences from Mongo")
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $seqFilter. " +
      s"Original query was for $roValues")
    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection, seqKey, seqFilter)
    methodLogger.info("Finished sequence query.")



    /*
      Map sequences and name to proteinSequences
     */
    methodLogger.info("Processing protein sequences")
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
    methodLogger.info("Writing sequences to FASTA file")
    writeProteinSequencesToFasta(proteinSequences.toList, context)
  }
}
