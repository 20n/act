package com.act.workflow.tool_manager.workflow.workflow_mixins.base

import java.io.FileOutputStream

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
    * @param outputWriter    The stream to write to.
    */
  def writeProteinSequencesToFasta(proteinSequence: ProteinSequence, outputStream: FileOutputStream) {
    val header: String = headerFormat.getHeader(proteinSequence)

    writeFastaHeader(header, outputStream)
    writeFastaSequence(proteinSequence, outputStream)
  }

  private def writeFastaHeader(header: String, outputStream: FileOutputStream): Unit = {
    // 62 = '>'
    outputStream.write(62)
    outputStream.write(header.getBytes())
    outputStream.write(lineSep)
  }

  private def writeFastaSequence(sequence: ProteinSequence, outputStream: FileOutputStream): Unit = {
    var characterCount: Integer = 0
    val seq: String = sequence.getSequenceAsString()

    for (i <- Range(0, seq.length)) {
      outputStream.write(seq.charAt(i))
      characterCount += 1
      if (characterCount == lineLength) {
        outputStream.write(lineSep)
        characterCount = 0
      }
    }

    if (sequence.getLength % lineLength != 0) {
      outputStream.write(lineSep)
    }
  }
}
