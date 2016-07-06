package com.act.analysis.proteome.files

import java.io.{File, PrintWriter}

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
class MultipleHmmFile(var sourceDirectory: String, hmmFileName: String) {
  val hmmPrefixName = hmmFileName.replaceAll("\\.hmm$", "")

  /**
    * Takes all the HMMs this class owns and maps them to individual files
    */
  def writeIndividualHmmsToFiles(): Unit = {
    // Writes the files to the location of the original HMM file, but in a directory
    val hmms = read()
    hmms.map(x => writeHmmToFile(x))
  }

  /**
    * Takes the source directory indicated on class creation and reads a
    * HMM file that contains multiple HMM profiles into individual HMMs
    *
    * @return List of tuples of (Pfam Name, HMM Information)
    */
  def read(): List[(String, String)] = {
    /**
      * Needs to be tail recursive otherwise will almost always stack overflow given large files
      *
      * @param lines  The lines still left in the file to process
      * @param buffer A buffer we add processed files to. This is needed to make things tail recursive
      * @return A list buffer containing all the HMMs we could find in the file.
      */
    @tailrec
    def parse(lines: Iterator[String], buffer: ListBuffer[(String, String)]): ListBuffer[(String, String)] = {
      if (lines.isEmpty) return buffer

      // Grab the next sample
      var header = lines.next()
      val (currentHmm, leftovers) = lines.span(x => !x.startsWith("//"))


      // Current HMM is an iterator, this allows us to save the rest of the file while we look for interesting things.
      val currentHmmList = currentHmm.toList

      // Unique ID for protein family will be the file name.
      val keyword = "ACC"
      // Processes the keyword and pretties the string up so it is just the protein family
      val hmmKeywordMaybe = currentHmmList.find(x => x.startsWith(keyword))

      val hmmKeyword = if (hmmKeywordMaybe.isDefined) hmmKeywordMaybe.get.split(keyword)(1).trim
      else "ProteinAtFilePosition_" + buffer.length

      /*
     The double slash indicates the end of the HMM, so we get rid of it when we find it in the header.
     The first element does not contain the double slash,
     so we need to catch this case and keep the header if it isn't the double slash.
      */
      if (header == "//") header = "" else header = header + "\n"
      buffer.append((hmmKeyword, s"$header${currentHmmList.mkString(sep = "\n")}\n//"))

      // Keep recursively adding to the buffer
      parse(leftovers, buffer)
    }

    // Recursively parse and add to a dynamic buffer.  Convert to immutable list in the end
    parse(scala.io.Source.fromFile(new File(sourceDirectory, hmmFileName)).getLines(),
      new ListBuffer[(String, String)]()).toList
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


    /*
    TODO Appears scala doesn't have try with resources, so it might be worthwhile to
    rewrite this as a normal try clause with a finally to close the writer to ensure it gets closed.
     */
    val writer = new PrintWriter(file)
    writer.write(hmmTuple._2)
    writer.close()
  }
}