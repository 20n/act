package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo.mongo_db_keywords.SequenceDatabaseKeywords
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence

import scala.collection.mutable.ListBuffer

trait QuerySequencesByReactionId extends MongoWorkflowUtilities with SequenceDatabaseKeywords {
  def querySequencesForSequencesByReactionId(reactionIds: List[AnyRef], mongoConnection: MongoDB): List[ProteinSequence] = {
    val methodLogger = LogManager.getLogger("querySequencesForSequencesByReactionId")

    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID

      Structure of query = (Rxnrefs -> In [ReactionIdsList])
    */

    val reactionList = new BasicDBList
    reactionIds.map(reactionList.add)

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = new BasicDBObject(SEQUENCE_DB_KEYWORD_RXN_REFS, defineMongoIn(reactionList))

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val seqFilter = new BasicDBObject
    seqFilter.put(SEQUENCE_DB_KEYWORD_ID, 1)
    seqFilter.put(SEQUENCE_DB_KEYWORD_SEQ, 1)
    seqFilter.put(SEQUENCE_DB_KEYWORD_ECNUM, 1)
    seqFilter.put(s"$SEQUENCE_DB_KEYWORD_METADATA.$SEQUENCE_DB_KEYWORD_NAME", 1)

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
      val seq = sequence.get(SEQUENCE_DB_KEYWORD_SEQ)
      // Enzymes may not have an enzyme number
      val num = if (sequence.get(SEQUENCE_DB_KEYWORD_ECNUM) != null) sequence.get(SEQUENCE_DB_KEYWORD_ECNUM) else "None"
      val id = sequence.get(SEQUENCE_DB_KEYWORD_ID)

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence so that we can use the FASTA file generator they provide.
        val newSeq = new ProteinSequence(seq.toString)

        // Enzymes may not have a name
        val metadataObject: DBObject = sequence.get(SEQUENCE_DB_KEYWORD_METADATA).asInstanceOf[DBObject]
        val name =
          if (metadataObject.get(SEQUENCE_DB_KEYWORD_NAME) != null)
            metadataObject.get(SEQUENCE_DB_KEYWORD_NAME)
          else "None"

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
