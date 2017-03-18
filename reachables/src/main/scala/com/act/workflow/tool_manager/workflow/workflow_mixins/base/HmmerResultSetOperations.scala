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

import java.io.{File, FileWriter}

import com.act.analysis.proteome.files.HmmResultParser

trait HmmerResultSetOperations {

  private val UNION_SET = "union.set"
  private val INTERSECTION_SET = "intersection.set"
  /**
    * On a list of hmmer result files, creates a file containing all the proteins available in those files.
    */
  def setUnionHmmerSearchResults(resultFile: List[File], setFileDirectory: String, roArg: String)(): Unit = {

    val setList = createSetFromHmmerResults(resultFile)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.union(set)
    }

    saveSet(new File(setFileDirectory, s"$roArg.$UNION_SET"), movingSet)
  }

  /**
    * On a list of HMMer result files,
    * creates a file containing the intersection between all the proteins in those files.
    */
  def setIntersectHmmerSearchResults(resultFile: List[File], setFileDirectory: String, roArg: String)(): Unit = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val setList = createSetFromHmmerResults(resultFile)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.intersect(set)
    }

    saveSet(new File(setFileDirectory, s"$roArg.$INTERSECTION_SET"), movingSet)
  }

  /**
    * Given a set of hmmer files, creates sets from their top-ranked sequences.
    */
  private def createSetFromHmmerResults(resultFileNames: List[File]): List[Set[String]] = {
    /*
      This is a List[List[HmmResultLines]]
      Given a set of result files, create a set of all proteins contained within, either disjoint or union

      Create list of sets
      Each member of the first list is a unique file, and the List[HmmResultLines] are all the lines from that file.
     */

    val resultFileLinesForEachFile = resultFileNames.map(HmmResultParser.parseFile)

    /*
      For each file in our list, as defined above, we map all the lines in that files to
      a list of their sequence names, and then turn that list of names into a set.
      Therefore, we get a List[Set[String]] where each member of
      List is a unique Set of Sequence Names found in that result file.
     */

    resultFileLinesForEachFile.map(x => x.map(y => y(HmmResultParser.HmmResultLine.SEQUENCE_NAME)).toSet)
  }

  /**
    * Sorts and saves the output set to a file
    *
    * @param file Where to save the file
    * @param set  The set which is to be saved
    */
  private def saveSet(file: File, set: Set[String]): Unit = {
    val orderedList = set.toList.sorted
    val writer = new FileWriter(file)

    // Headers
    writer.write("Set compare data file\n")
    writer.write(s"File type: ${file.getName}\n")
    writer.write("Proteins in set:\n")

    for (entry <- orderedList) {
      writer.write(s"$entry\n")
    }

    writer.close()
  }
}
