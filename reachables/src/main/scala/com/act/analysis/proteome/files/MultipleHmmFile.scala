package com.act.analysis.proteome.files

import java.io.{File, PrintWriter}

import breeze.linalg.split

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Used to take a HMM file with multiple HMM profiles and extract each individually,
  * placing them in a directory as individual files.
  *
  * The basic unit of a HMM File is as shown below
  * <Header Elements>
  * <HMM Information>
  * <Ending character (//)>
  *
  * This is then repeated for each HMM in the file.
  *
  * @param sourceDirectory The directory where we will find the HMM file by the name of hmmFileName
  * @param hmmFileName     Name of the actual HMM file
  */
class MultipleHmmFile(var sourceDirectory: String, hmmFileName: String) extends Hmm {
  val hmmPrefixName = hmmFileName.replaceAll("\\.hmm$", "")

  /**
    * Takes the source directory indicated on class creation and reads a
    * HMM file that contains multiple HMM profiles into individual HMMs
    *
    * @return List of tuples of (Pfam Name, HMM Information)
    */
  def readAndWriteMiniFiles(): Unit = {
    /**
      * Needs to be tail recursive otherwise will almost always stack overflow given large files
      *
      * @param lines  The lines still left in the file to process
      * @param buffer A buffer we add processed files to. This is needed to make things tail recursive
      *
      * @return A list buffer containing all the HMMs we could find in the file.
      */
    var unnamedCount = 0
    var c = 0



    def parse(lines: Iterator[String]):Unit = {
      var currentInformation = ""
      while(lines.hasNext) {
        var hmmKeyword: String = ""
        val currentLine = lines.next()
        currentInformation += currentLine + "\n"

        if (currentLine.startsWith(HmmHeaderDesignations.Pfam.toString)) {
          hmmKeyword = currentLine.split(HmmHeaderDesignations.Pfam.toString)(1).trim
        }

        if (currentLine.startsWith("//")) {
          writeHmmToFile((hmmKeyword, s"$currentInformation"))
          currentInformation = ""
          hmmKeyword = ""
        }
      }
    }

    // Recursively parse and add to a dynamic buffer.  Convert to immutable list in the end
    parse(scala.io.Source.fromFile(new File(sourceDirectory, hmmFileName)).getLines())
  }

  /**
    *
    * @param hmmTuple An individual tuple as composed from parse. Tuple = (Pfam name, HMM information)
    */
  def writeHmmToFile(hmmTuple: (String, String)) {
    // Need to make sure the file path exists.
    val storageDirectory = new File(sourceDirectory, hmmPrefixName)
    storageDirectory.mkdirs()

    val file = new File(storageDirectory.getAbsolutePath, hmmTuple._1 + ".hmm")
    if (!file.exists) {
      val writer = new PrintWriter(file)
      writer.write(hmmTuple._2)
      writer.close()
    }
  }
}