package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import java.io.{File, FileOutputStream}

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WriteProteinSequencesToFasta
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence

trait QueryByReactionId extends MongoWorkflowUtilities with WriteProteinSequencesToFasta with SequenceDatabaseKeywords {
  /**
    * Takes in a list of reaction IDs and creates outputs a list of ProteinSequences known to do those reactions.
    *
    * @param reactionIds List of reaction IDs.
    * @param mongoConnection Connection to MongoDB
    *
    * @return List of protein sequences.
    */
  def querySequencesForSequencesByReactionId(reactionIds: List[Long], outputFile: File, mongoConnection: MongoDB): Unit = {
    val methodLogger = LogManager.getLogger("querySequencesForSequencesByReactionId")

    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID

      Structure of query = (Rxnrefs -> In [ReactionIdsList])
    */

    val reactionList = new BasicDBList
    reactionIds.map(rId => reactionList.add(rId.asInstanceOf[AnyRef]))

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
    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection, seqKey, seqFilter)
    methodLogger.info("Finished sequence query.")



    /*
      Map sequences and name to proteinSequences
    */
    val outputStream: FileOutputStream = new FileOutputStream(outputFile)
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
        writeProteinSequencesToFasta(newSeq, outputStream)
      } else {
        methodLogger.error(s"Sequence identified that does not have a sequence.  DB entry is ${id.toString}")
      }
    }
  }
}
