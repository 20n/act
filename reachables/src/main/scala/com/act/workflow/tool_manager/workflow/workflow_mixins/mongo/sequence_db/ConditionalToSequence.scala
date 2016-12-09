package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import java.io.{BufferedWriter, File, FileWriter}

import act.shared.{Seq => DbSeq}
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WriteProteinSequenceToFasta
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, MongoWorkflowUtilities, SequenceKeywords}
import com.mongodb.DBObject
import org.biojava.nbio.core.sequence.ProteinSequence

import scala.collection.JavaConverters._

trait ConditionalToSequence extends WriteProteinSequenceToFasta with QueryBySequenceId with MongoWorkflowUtilities {
  def getIdsForEachDocumentInConditional(database: String)(conditional: String): Stream[DbSeq] = {
    val mongoConnection = connectToMongoDatabase(database)

    val seqQuery = createDbObject(SequenceKeywords.SEQ, createDbObject(MongoKeywords.NOT_EQUAL, null))
    seqQuery.put(MongoKeywords.WHERE.toString, conditional)

    val matchingSequences: Iterator[DbSeq] = mongoConnection.getSeqIterator(seqQuery).asScala

    matchingSequences.toStream
  }

  def writeFastaFileForEachDocument(database: String, outputFile: File)(sequenceId: Long)(): Unit ={
    val mongoConnection = connectToMongoDatabase(database)
    val matchingSequence = querySequencesBySequenceId(List(sequenceId), mongoConnection, List()).next()

    sequenceObjectToFasta(outputFile, matchingSequence)
  }

  def sequenceObjectToFasta(outputFile: File, document: DBObject): Unit = {
    /*
      Map sequences and name to proteinSequences
    */
    val outputWriter = new BufferedWriter(new FileWriter(outputFile))

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
    }

    outputWriter.close()
  }
}
