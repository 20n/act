package com.act.analysis.proteome.files

import java.io.File

import org.apache.logging.log4j.LogManager

import scala.annotation.tailrec
import scala.collection.mutable


object AlignedFastaFileParser {

  val logger = LogManager.getLogger(getClass.getName)

  /*
    An arbitrary mapping that consistently one-hot encodes aligned amino acids.
    The one-hot encoding size is MapSize - 1, so this gives us the index to assign a "1" to for a given character.
   */
  val characterMap: Map[Char, Int] = Map[Char, Int](
    '-' -> -1,
    'R' -> 0,
    'N' -> 1,
    'D' -> 2,
    'C' -> 3,
    'Q' -> 4,
    'E' -> 5,
    'G' -> 6,
    'H' -> 7,
    'I' -> 8,
    'L' -> 9,
    'K' -> 10,
    'M' -> 11,
    'F' -> 12,
    'P' -> 13,
    'S' -> 14,
    'T' -> 15,
    'W' -> 16,
    'Y' -> 17,
    'V' -> 18,
    'A' -> 19
  )
  private val NEW_PROTEIN_INDICATOR = ">"

  def parseFile(fastaFile: File): List[(String, String)] = {
    if (!fastaFile.exists()) {
      val message = s"Supplied pre-aligned fasta file supplied does not exist.  " +
        s"Supplied file had a path of ${fastaFile.getAbsolutePath}."
      logger.error(message)
      throw new RuntimeException(message)
    }

    // We use a linked hash map here because the order matters in regards to repeatability.
    val alignedProteins = mutable.LinkedHashMap[String, String]()

    // Get an iterator over the given file
    val lines: Iterator[String] = scala.io.Source.fromFile(fastaFile).getLines()

    @tailrec
    def parser(iterator: Iterator[String]): Unit = {
      if (!iterator.hasNext) return

      // Convert current protein -> strings
      val header = iterator.next().mkString
      val (proteinSequence, remaining) = iterator.span(!_.startsWith(NEW_PROTEIN_INDICATOR))
      val protein: String = proteinSequence.mkString

      // Add results to buffer
      alignedProteins(header) = protein

      // Parse the rest of the file.
      parser(remaining)
    }
    parser(lines)

    alignedProteins.toList
  }
}
