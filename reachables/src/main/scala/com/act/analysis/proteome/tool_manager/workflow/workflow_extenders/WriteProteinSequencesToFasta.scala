package com.act.analysis.proteome.tool_manager.workflow.workflow_extenders

import java.io.File

import org.apache.logging.log4j.LogManager
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.io.FastaWriterHelper

import scala.collection.JavaConverters._

trait WriteProteinSequencesToFasta {
  val OPTION_OUTPUT_FASTA_FILE_PREFIX: String

  /*
   Write to output
  */
  def writeProteinSequencesToFasta(proteinSequences: List[ProteinSequence], context: Map[String, Any]) {
    val methodLogger = LogManager.getLogger("writeProteinSequencesToFasta")
    val outputFasta = context(OPTION_OUTPUT_FASTA_FILE_PREFIX).toString

    if (proteinSequences.length < 1) {
      methodLogger.error("No sequences found after filtering for values with no sequences")
      throw new RuntimeException("No sequences found, invalid run.")
    } else {
      methodLogger.info(s"Writing ${proteinSequences.length} sequences to Fasta file at $outputFasta.")
      FastaWriterHelper.writeProteinSequence(new File(outputFasta),
        proteinSequences.asJavaCollection)
    }
  }
}
