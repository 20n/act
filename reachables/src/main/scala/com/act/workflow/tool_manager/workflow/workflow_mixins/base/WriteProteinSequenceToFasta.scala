/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
    * @param outputWriter    The stream to write to.
    */
  def writeProteinSequenceToFasta(proteinSequence: ProteinSequence, outputWriter: BufferedWriter) {
    val header: String = headerFormat.getHeader(proteinSequence)

    writeFastaHeader(header, outputWriter)
    writeFastaSequence(proteinSequence, outputWriter)
  }

  private def writeFastaHeader(header: String, outputWriter: BufferedWriter): Unit = {
    outputWriter.write(">")
    outputWriter.write(header)
    outputWriter.newLine()
  }

  private def writeFastaSequence(sequence: ProteinSequence, outputWriter: BufferedWriter): Unit = {
    var characterCount: Integer = 0
    val seq: String = sequence.getSequenceAsString()

    for (i <- Range(0, seq.length)) {
      outputWriter.write(seq.charAt(i))
      characterCount += 1
      if (characterCount == lineLength) {
        outputWriter.newLine()
        characterCount = 0
      }
    }

    if (sequence.getLength % lineLength != 0) {
      outputWriter.newLine()
    }
  }
}
