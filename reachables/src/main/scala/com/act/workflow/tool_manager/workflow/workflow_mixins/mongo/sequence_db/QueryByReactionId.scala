/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import java.io.{BufferedWriter, File, FileWriter}

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WriteProteinSequenceToFasta
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, SequenceKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence

trait QueryByReactionId extends MongoWorkflowUtilities with WriteProteinSequenceToFasta {

  /**
    * Takes in a list of reaction IDs and creates outputs a list of ProteinSequences known to do those reactions.
    *
    * @param reactionIds List of reaction IDs.
    * @param mongoConnection Connection to MongoDB
    *
    * @return List of protein sequences.
    */
  def createFastaByReactionId(reactionIds: List[Long], outputFile: File, mongoConnection: MongoDB, organismRegex: Option[String] = None): Unit = {
    val methodLogger = LogManager.getLogger("querySequencesForSequencesByReactionId")

    // We want back the sequence, enzyme number, name, and the ID in our DB.
    val returnFields = List(SequenceKeywords.ID.toString,
      SequenceKeywords.SEQ.toString,
      SequenceKeywords.ECNUM.toString,
      s"${SequenceKeywords.METADATA.toString}.${SequenceKeywords.NAME.toString}")

    val returnSequenceDocuments: Iterator[DBObject] =
      querySequencesMatchingReactionIdIterator(reactionIds, mongoConnection, returnFields, organismRegex)


    /*
      Map sequences and name to proteinSequences
    */
    val outputWriter = new BufferedWriter(new FileWriter(outputFile))
    for (document: DBObject <- returnSequenceDocuments) {

      val id = document.get(SequenceKeywords.ID.toString)
      val seq = document.get(SequenceKeywords.SEQ.toString)

      // Enzymes may not have an enzyme number
      val ecnum: String = getWithDefault(document, SequenceKeywords.ECNUM, "None")

      // Make sure it has a sequence
      if (seq != null) {
        // Map sequence to BioJava protein sequence so that we can use the FASTA file generator they provide.
        val newSeq = new ProteinSequence(seq.toString)

        // Enzymes may not have a name
        val nameLocation = s"${SequenceKeywords.METADATA.toString}.${SequenceKeywords.NAME.toString}"
        val name: String = getWithDefault(document, nameLocation, "None")

        /*
          These headers are required to be unique or else downstream software will likely crash.
          This header may not be unique based on Name/EC number alone (For example, if they are both none),
          but the DB_ID should guarantee uniqueness
        */
        newSeq.setOriginalHeader(s"NAME: $name | EC: $ecnum | DB_ID: ${id.toString}")
        writeProteinSequenceToFasta(newSeq, outputWriter)
      } else {
        methodLogger.error(s"Sequence identified that does not have a sequence.  DB entry is ${id.toString}")
      }
    }
    outputWriter.close()
  }

  /**
    * Query sequences based on if the sequence references one of the reactions in the reactionIds list.
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
  def querySequencesMatchingReactionIdIterator(reactionIds: List[Long],
                                               mongoConnection: MongoDB,
                                               returnFilterFields: List[String],
                                               organismRegex: Option[String] = None): Iterator[DBObject] = {
    val methodLogger = LogManager.getLogger("querySequencesMatchingReactionIdIterator")

    /*
      Query sequence database for enzyme sequences by looking for enzymes that have an rID

      Structure of query = (Rxnrefs -> In [ReactionIdsList])
    */


    val reactionList = new BasicDBList
    reactionIds.map(rId => reactionList.add(rId.asInstanceOf[AnyRef]))

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = createDbObject(SequenceKeywords.RXN_REFS, defineMongoIn(reactionList))
    if (organismRegex.isDefined) {
      appendKeyToDbObject(seqKey, SequenceKeywords.ORGANISM_NAME, defineMongoRegex(organismRegex.get))
    }


    val reactionIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      reactionIdReturnFilter.append(field, 1)
    }

    methodLogger.info("Querying enzymes with the desired reactions for sequences from Mongo")
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $reactionIdReturnFilter. ")
    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection)(seqKey, reactionIdReturnFilter)
    methodLogger.info("Finished sequence query.")

    sequenceReturnIterator
  }
}

