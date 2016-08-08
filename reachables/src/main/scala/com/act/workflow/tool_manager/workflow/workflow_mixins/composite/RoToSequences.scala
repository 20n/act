package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WriteProteinSequencesToFasta
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import org.apache.logging.log4j.LogManager

trait RoToSequences extends QueryByRo with QueryByReactionId with WriteProteinSequencesToFasta {
  /**
    * Takes in a set of ROs and translates them into FASTA files with all the enzymes that do that RO
    */
  def writeFastaFileFromEnzymesMatchingRos(roValues: List[String], outputFasta: String)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingRos")
    val mongoConnection = connectToMongoDatabase("marvin")

    val reactionIds = queryReactionsForReactionIdsByRo(roValues, mongoConnection)
    val proteinSequences = querySequencesForSequencesByReactionId(reactionIds, mongoConnection)

    methodLogger.info("Writing sequences to FASTA file")
    writeProteinSequencesToFasta(proteinSequences, outputFasta)
  }
}
