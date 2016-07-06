package com.act.analysis.proteome.files

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Created by michaellampe on 7/5/16.
  */
class ProteinFile(fileName: String) {
  val sequences = readFastaFile(fileName)


  def readFastaFile(fileName: String): List[(String, String)] = {
    // Tail recursive prevents stack overflow on large files.
    @tailrec
    def parse(lines: Iterator[String], returnList: ListBuffer[(String, String)]): ListBuffer[(String, String)] = {
      if (lines.isEmpty) return returnList
      if (returnList.length % 1000 == 0) println("Accrued protein number is " + returnList.length)

      // Process the file from header -> end of sequence
      val header = lines.next()
      val (sequence, leftovers) = lines.span(x => x(0) != '>')

      // Add to building list buffer
      returnList.append((header, sequence.mkString))


      parse(leftovers, returnList)
    }

    // Make immutable at end
    parse(scala.io.Source.fromFile(fileName).getLines(), ListBuffer[(String, String)]()).toList
  }

  // TODO: Save the array as a binary file so we aren't processing these files constantly while testing stuff.
  def saveAsBinaryFile(): Unit = {
    // Not necessary currently
  }

}
