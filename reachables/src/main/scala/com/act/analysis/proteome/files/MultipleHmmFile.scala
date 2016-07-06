package com.act.analysis.proteome.files

import java.io._

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
  * Used to take a HMM file with multiple HMM profiles and extract each individually,
  * placing them in a directory as individual files.
  *
  * @param sourceDirectory - The directory where we will find the HMM file by the name of hmmFileName
  * @param hmmFileName     - Name of the actual HMM file
  */
class MultipleHmmFile(var sourceDirectory: String, hmmFileName: String) {
  // Add trailing slash for nice file pathing
  if (!sourceDirectory.endsWith("/")) sourceDirectory = sourceDirectory + "/"
  val hmms = readMultipleHmmFile()
  val hmmPrefixName = hmmFileName.replace(".hmm", "") + "/"

  /**
    * Takes the source directory indicated on class creation and reads a
    * HMM file that contains multiple HMM profiles into individual HMMs
    *
    * @return List of tuples of (Pfam Name, HMM Information)
    */
  def readMultipleHmmFile(): List[(String, String)] = {
    /**
      * Needs to be tail recursive otherwise will almost always stack overflow given large files
      *
      * @param lines  - The lines still left in the file to process
      * @param buffer - A buffer we add processed files to. This is needed to make things tail recursive
      * @return A list buffer containing all the HMMs we could find in the file.
      */
    @tailrec
    def parse(lines: Iterator[String], buffer: ListBuffer[(String, String)]): ListBuffer[(String, String)] = {
      if (lines.isEmpty) return buffer

      // Grab the next sample
      var header = lines.next()
      val (currentHmm, leftovers) = lines.span(x => !x.startsWith("//"))

      /*
       The double slash indicates the end of the HMM, so we get rid of it when we find it in the header.
       The first element does not contain the double slash,
       so we need to catch this case and keep the header if it isn't the double slash.
        */
      if (header == "//") header = "" else header = header + "\n"

      val currentHmmList = List() ++ currentHmm

      // Unique ID for protein family will be the file name.
      val keyword = "ACC"
      // Processes the keyword and pretties the string up so it is just the protein family
      val hmmKeyword = currentHmmList.find(x => x.startsWith(keyword)).mkString.split(keyword)(1).trim()

      buffer.append((hmmKeyword, s"$header${currentHmmList.mkString(sep = "\n")}\n//"))

      // Keep recursively adding to the buffer
      parse(leftovers, buffer)
    }

    // Recursively parse and add to a dynamic buffer.  Convert to immutable list in the end
    parse(scala.io.Source.fromFile(sourceDirectory + hmmFileName).getLines(), new ListBuffer[(String, String)]()).toList
  }

  /**
    * Takes all the HMMs this class owns and maps them to individual files
    */
  def writeIndividualHmmsToFiles(): Unit = {
    // Writes the files to the location of the original HMM file, but in a directory
    hmms.map(x => writeHmmToFile(x))
  }

  /**
    *
    * @param hmmTuple An individual tuple as composed from parse. Tuple = (Pfam name, HMM information)
    */
  def writeHmmToFile(hmmTuple: (String, String)) {
    val filePath = s"$sourceDirectory$hmmPrefixName${hmmTuple._1}.hmm"

    // Need to make sure the file path exists.
    val file = new File(filePath)
    file.getParentFile.mkdirs()

    /*
    TODO Appears scala doesn't have try with resources, so it might be worthwhile to rewrite this as a normal try clause with a finally to close the writer to ensure it gets closed.
     */
    val writer = new PrintWriter(file)
    writer.write(hmmTuple._2)
    writer.close()
  }
}
