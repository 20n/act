package com.act.analysis.proteome.utilities

import com.act.analysis.proteome.sequences.{DNA, RNA}

/**
  * Takes in a DNA or RNA sequence and converts it to protein.
  *
  * Can output more than one sequence, especially if a frame is not given.
  * There is a threshold for protein size, which can be modified by the user.
  */
object SequenceToProteinConverter {
  // Map a given file extension to the appropriate processing method
  private val fileMap = Map(
    ("fasta", getSequenceFromFastaFile _),
    ("fsa", getSequenceFromFastaFile _)
  )

  def loadFileAndConvertToProteins(fileName: String): List[String] = {
    val fileEnding = fileName.split("\\.")(1)

    val sequence = fileMap(fileEnding)(fileName)
    convertDnaToProtein(sequence)
  }

  private def convertDnaToProtein(sequence: String): List[String] = {
    // We take both as technically there could be proteins on either
    val dnaSequence = DNA.translate(sequence)
    convertRnaToProtein(dnaSequence)
  }

  private def convertRnaToProtein(sequence: String): List[String] = {
    RNA.translate(sequence)
  }

  private def getSequenceFromFastaFile(fileName: String): String = {
    val fileLines = scala.io.Source.fromFile(fileName).getLines()
    // get rid of header
    fileLines.next()
    fileLines.mkString.toLowerCase
  }
}
