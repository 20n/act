package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite

import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.WriteProteinSequencesToFasta
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo.{QueryByRo, QuerySequencesByReactionId}
import org.apache.logging.log4j.LogManager

trait RoToSequences extends QueryByRo with QuerySequencesByReactionId with WriteProteinSequencesToFasta {

  /**
    * Takes in a set of ROs and translates them into FASTA files with all the enzymes that do that RO
    *
    */
  def writeFastaFileFromEnzymesMatchingRos(roValues: List[String], outputFasta: String)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingRos")
    val mongoConnection = connectToMongoDatabase()

    val reactionIds = queryReactionsForReactionIdsByRo(roValues, mongoConnection)
    val proteinSequences = querySequencesForSequencesByReactionId(reactionIds, mongoConnection)

    methodLogger.info("Writing sequences to FASTA file")
    writeProteinSequencesToFasta(proteinSequences, outputFasta)
  }
}
