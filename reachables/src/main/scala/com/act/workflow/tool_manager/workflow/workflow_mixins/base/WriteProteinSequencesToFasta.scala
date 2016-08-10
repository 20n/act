package com.act.workflow.tool_manager.workflow.workflow_mixins.base

import java.io.OutputStream

import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound
import org.biojava.nbio.core.sequence.io.GenericFastaHeaderFormat

trait WriteProteinSequencesToFasta {
  private val lineLength: Integer = 60
  private val lineSep: Array[Byte] = System.getProperty("line.separator").getBytes()
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
  def writeProteinSequencesToFasta(proteinSequence: ProteinSequence, outputStream: OutputStream) {
    val header = headerFormat.getHeader(proteinSequence)

    // 62 = '>'
    outputStream.write(62)
    outputStream.write(header.getBytes())
    outputStream.write(lineSep)

    var compoundCount: Integer = 0

    val seq: String = proteinSequence.getSequenceAsString()

    for (i <- Range(0, seq.length)) {
      outputStream.write(seq.charAt(i))
      compoundCount += 1
      if (compoundCount == lineLength) {
        outputStream.write(lineSep)
        compoundCount = 0
      }
    }

    if (proteinSequence.getLength % lineLength != 0) {
      outputStream.write(lineSep)
    }
  }
}
