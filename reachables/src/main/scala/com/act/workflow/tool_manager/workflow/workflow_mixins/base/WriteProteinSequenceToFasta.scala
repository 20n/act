package com.act.workflow.tool_manager.workflow.workflow_mixins.base

import java.io.BufferedWriter

import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound
import org.biojava.nbio.core.sequence.io.GenericFastaHeaderFormat

trait WriteProteinSequenceToFasta {
  private val lineLength: Integer = 60
  private val headerFormat = new GenericFastaHeaderFormat[ProteinSequence, AminoAcidCompound]()

  /**
    * This class is copied over from BioJava's implementation of FastaWriter.
    * We reimplement this here as if we want to write just one
    * proteinSequence BioJava makes us create a new FastaWriter class or pass an entire list of sequences...
    * This is much more streamlined and does not falter when the number of sequences gets very large
    * (At about ~300k of sequences things start to get slow/stop, this method that doesn't happen).
    *
    * @param proteinSequence A given protein sequence instance
    * @param outputStream    The stream to write to.
    */
  def writeProteinSequenceToFasta(proteinSequence: ProteinSequence, outputStream: BufferedWriter) {
    val header: String = headerFormat.getHeader(proteinSequence)

    writeFastaHeader(header, BufferedWriter)
    writeFastaSequence(proteinSequence, BufferedWriter)
  }

  private def writeFastaHeader(header: String, outputStream: BufferedWriter): Unit = {
    outputStream.write(">")
    outputStream.write(header)
    outputStream.newLine()
  }

  private def writeFastaSequence(sequence: ProteinSequence, outputStream: BufferedWriter): Unit = {
    var characterCount: Integer = 0
    val seq: String = sequence.getSequenceAsString()

    for (i <- Range(0, seq.length)) {
      outputStream.write(seq.charAt(i))
      characterCount += 1
      if (characterCount == lineLength) {
        outputStream.newLine()
        characterCount = 0
      }
    }

    if (sequence.getLength % lineLength != 0) {
      outputStream.newLine()
    }
  }
}
