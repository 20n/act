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

package com.act.analysis.proteome.files

import java.io.{File, PrintWriter}

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


  /* By writing this iteratively,
   we don't run into stack overflows caused
   by the iterator on large files when writing a tail recursive version
  */
  def readAndWriteMiniFiles(): Unit = {
    def parse(lines: Iterator[String]):Unit = {
      var currentInformation = ""
      while(lines.hasNext) {
        // Set local variables
        var hmmKeyword: String = ""
        val currentLine = lines.next()

        // Set as next line
        currentInformation += currentLine + "\n"

        // Extract the Pfam to name the file
        if (currentLine.startsWith(HmmHeaderDesignations.Pfam.toString)) {
          hmmKeyword = currentLine.split(HmmHeaderDesignations.Pfam.toString)(1).trim
        }

        // The double slash indicates end of an HMM.  Write and reset local variables here
        if (currentLine.startsWith("//")) {
          writeHmmToFile((hmmKeyword, s"$currentInformation"))
          currentInformation = ""
          hmmKeyword = ""
        }
      }
    }

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