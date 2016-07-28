package com.act.analysis.proteome.tool_manager.workflow.workflow_extenders

import java.io.{File, FileWriter}

import com.act.analysis.proteome.files.HmmResultParser

trait HmmerResultSetOperations {
  val SET_LOCATION: String
  val RO_ARG_PREFIX: String
  val OPTION_RESULT_FILE_PREFIX: String


  /**
    * On a list of hmmer result files, creates a file containing all the proteins available in those files.
    *
    * @param context Passes which RO arg and the location that the set should be stored at.
    */
  def setUnionCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.union(set)
    }

    saveSet(new File(context(SET_LOCATION).asInstanceOf[String], s"${context(RO_ARG_PREFIX)}.union.set"), movingSet)
  }

  /**
    * On a list of hmmer result files,
    * creates a file containing the intersection between all the proteins in those files.
    *
    * @param context Passes which RO arg and the location that the set should be stored at.
    */
  def setIntersectionCompareOfHmmerSearchResults(context: Map[String, Any]): Unit = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val setList = createSetFromHmmerResults(context)

    // Sequentially apply sets
    var movingSet = setList.head
    for (set <- setList.tail) {
      movingSet = movingSet.intersect(set)
    }
    saveSet(new File(
      context(SET_LOCATION).asInstanceOf[String],
      s"${context(RO_ARG_PREFIX)}.intersection.set"),
      movingSet)
  }

  /**
    * Given a set of hmmer files, creates sets from their top-ranked sequences.
    *
    * @param context Passes all the result files.
    *
    * @return
    */
  private def createSetFromHmmerResults(context: Map[String, Any]): List[Set[String]] = {
    // Given a set of result files, create a set of all proteins contained within, either disjoint or union
    val resultFiles = context(OPTION_RESULT_FILE_PREFIX).asInstanceOf[List[String]]

    // Create list of sets
    val fileList = resultFiles.map(HmmResultParser.parseFile)
    fileList.map(x => x.map(y => y(HmmResultParser.HmmResultLine.SEQUENCE_NAME)).toSet)
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
    writer.write("Set compare data file\n")
    writer.write(s"File type: ${file.getName}\n")
    writer.write("Proteins in set:\n")
    for (entry <- orderedList) {
      writer.write(s"$entry\n")
    }
    writer.close()
  }
}
