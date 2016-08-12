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

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val returnFields = List(SEQUENCE_DB_KEYWORD_ID,
      SEQUENCE_DB_KEYWORD_SEQ,
      SEQUENCE_DB_KEYWORD_ECNUM,
      s"$SEQUENCE_DB_KEYWORD_METADATA.$SEQUENCE_DB_KEYWORD_NAME")

    val returnSequenceDocuments: Iterator[DBObject] =
      querySequencesMatchingReactionIdIterator(reactionIds, mongoConnection, returnFields)


    /*
      Map sequences and name to proteinSequences
    */
    val outputStream = new FileOutputStream(outputFile)
    for (document: DBObject <- returnSequenceDocuments) {

      val id = document.get(SEQUENCE_DB_KEYWORD_ID)
      val seq = document.get(SEQUENCE_DB_KEYWORD_SEQ)

      // Enzymes may not have an enzyme number
      val ecnum = if (document.get(SEQUENCE_DB_KEYWORD_ECNUM) != null)
        document.get(SEQUENCE_DB_KEYWORD_ECNUM)
      else "None"

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence so that we can use the FASTA file generator they provide.
        val newSeq = new ProteinSequence(seq.toString)

        // Enzymes may not have a name
        val name = if (document.get(s"$SEQUENCE_DB_KEYWORD_METADATA.$SEQUENCE_DB_KEYWORD_NAME") != null)
          document.get(s"$SEQUENCE_DB_KEYWORD_METADATA.$SEQUENCE_DB_KEYWORD_NAME")
        else "None"

        /*
          These headers are required to be unique or else downstream software will likely crash.
          This header may not be unique based on Name/EC number alone (For example, if they are both none),
          but the DB_ID should guarantee uniqueness
        */
        newSeq.setOriginalHeader(s"NAME: ${name.toString} | EC: ${ecnum.toString} | DB_ID: ${id.toString}")
        writeProteinSequencesToFasta(newSeq, outputStream)
      } else {
        methodLogger.error(s"Sequence identified that does not have a sequence.  DB entry is ${id.toString}")
      }
    }
    outputStream.close()
  }

  /**
    * Sometimes queries can be too high memory if we want to map all the fields to values.
    * Thus, this method allows, for the mapping step to be skipped to conserve memory and
    * just returns the iterator of DB objects.
    *
    * @param reactionIds        List of reaction IDs.
    * @param mongoConnection    Connection to MongoDB
    * @param returnFilterFields The fields you are looking for.
    *
    * @return An iterator over the documents that matched your query.
    */
  private def querySequencesMatchingReactionIdIterator(reactionIds: List[Long],
                                                       mongoConnection: MongoDB,
                                                       returnFilterFields: List[String]): Iterator[DBObject] = {
    val methodLogger = LogManager.getLogger("querySequencesMatchingReactionIdIterator")

    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID

      Structure of query = (Rxnrefs -> In [ReactionIdsList])
    */


    val reactionList = new BasicDBList
    reactionIds.map(rId => reactionList.add(rId.asInstanceOf[AnyRef]))

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = new BasicDBObject(SEQUENCE_DB_KEYWORD_RXN_REFS, defineMongoIn(reactionList))

    val reactionIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      reactionIdReturnFilter.append(field, 1)
    }

    methodLogger.info("Querying enzymes with the desired reactions for sequences from Mongo")
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $reactionIdReturnFilter. ")
    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection, seqKey, reactionIdReturnFilter)
    methodLogger.info("Finished sequence query.")

    sequenceReturnIterator
  }

  /**
    * Query sequences based on if they contain a reaction ID
    *
    * @param reactionIds        A list of reactionIds, a matching sequence will match one or more.
    * @param mongoConnection    Connection to Mongo database
    * @param returnFilterFields The fields you are looking for.
    *
    * @return Returns a map of documents with their fields as the secondary keys.
    *         First map is keyed by the document ID, secondary maps are keyed by the field names retrieved from the DB.
    */
  def querySequencesForValuesByReactionId(reactionIds: List[Long],
                                          mongoConnection: MongoDB,
                                          returnFilterFields: List[String]): Map[Long, Map[String, AnyRef]] = {

    val methodLogger = LogManager.getLogger("querySequencesForSequencesByReactionId")

    val sequenceReturnIterator = querySequencesMatchingReactionIdIterator(reactionIds, mongoConnection, returnFilterFields)

    val sequenceDocuments = mongoReturnQueryToMap(sequenceReturnIterator, returnFilterFields)
    methodLogger.info(s"Found ${sequenceDocuments.size} document${if (sequenceDocuments.size != 1) "s" else ""}.")
    sequenceDocuments
  }
}
