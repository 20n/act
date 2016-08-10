package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import java.io.File

import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WriteProteinSequencesToFasta
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByEcNumber
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import org.apache.logging.log4j.LogManager

trait EcnumToSequences extends QueryByEcNumber with QueryByReactionId with WriteProteinSequencesToFasta {
  /**
    * Takes in a ecnum and translates them into FASTA files with all the enzymes that do that Ecnum
    */
  def writeFastaFileFromEnzymesMatchingEcnums(roughEcnum: String, outputFastaFile: File, database: String)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingEcnums")

    val mongoConnection = connectToMongoDatabase(database)

    // Documents are keyed on their IDs
    val reactionIds = queryReactionsForReactionIdsByEcNumber(roughEcnum, mongoConnection)
    methodLogger.info("Discovering and writing sequences to FASTA file")
    querySequencesForSequencesByReactionId(reactionIds.keySet.toList, outputFastaFile, mongoConnection)
  }
}
