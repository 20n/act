package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.composite

import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base.WriteProteinSequencesToFasta
import com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo.{QueryByEcNumber, QuerySequencesByReactionId}
import org.apache.logging.log4j.LogManager

trait EcnumToSequences extends QueryByEcNumber with QuerySequencesByReactionId with WriteProteinSequencesToFasta {
  /**
    * Takes in a ecnum and translates them into FASTA files with all the enzymes that do that Ecnum
    *
    */
  def writeFastaFileFromEnzymesMatchingEcnums(roughEcnum: String, outputFilePath: String)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingEcnums")

    val mongoConnection = connectToMongoDatabase()

    val reactionIds = queryReactionsForReactionIdsByEcNumber(roughEcnum, mongoConnection)
    val proteinSequences = querySequencesForSequencesByReactionId(reactionIds, mongoConnection)

    methodLogger.info("Writing sequences to FASTA file")
    writeProteinSequencesToFasta(proteinSequences, outputFilePath)

  }
}
