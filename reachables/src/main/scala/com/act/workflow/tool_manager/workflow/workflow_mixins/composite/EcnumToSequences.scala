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
  def writeFastaFileFromEnzymesMatchingEcnums(roughEcnum: String, outputFastaFile: File)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingEcnums")

    val mongoConnection = connectToMongoDatabase("marvin")

    val reactionIds = queryReactionsForReactionIdsByEcNumber(roughEcnum, mongoConnection)
    val proteinSequences = querySequencesForSequencesByReactionId(reactionIds.keySet.toList, mongoConnection)

    methodLogger.info("Writing sequences to FASTA file")
    writeProteinSequencesToFasta(proteinSequences, outputFastaFile)
  }
}
