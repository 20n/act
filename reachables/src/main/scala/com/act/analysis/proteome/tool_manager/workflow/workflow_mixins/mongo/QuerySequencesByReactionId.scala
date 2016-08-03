package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence

import scala.collection.mutable.ListBuffer

trait QuerySequencesByReactionId extends MongoWorkflowUtilities {
  val ECNUM = "ecnum"
  val SEQ = "seq"
  val METADATA = "metadata"
  val NAME = "name"
  val ID = "_id"
  val RXN_REFS = "rxn_refs"
  val MECHANISTIC_VALIDATOR = "mechanistic_validator_result"

  def querySequencesForSequencesByReactionId(reactionIds: List[String], mongoConnection: MongoDB): List[ProteinSequence] = {
    val methodLogger = LogManager.getLogger("querySequencesForSequencesByReactionId")
    /*
    Query sequence database for enzyme sequences by looking for enzymes that have an rID
   */
    // Structure of query = (Rxnrefs -> In [ReactionIdsList])
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
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $seqFilter. ")
    val sequenceReturnIterator: Iterator[DBObject] =
      mongoQuerySequences(mongoConnection, seqKey, seqFilter)
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
      } else {
        methodLogger.error(s"Sequence identified that does not have a sequence.  DB entry is ${id.toString}")
      }
    }

    proteinSequences.toList
  }
}
