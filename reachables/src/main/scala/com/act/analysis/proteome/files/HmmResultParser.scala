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

import java.io.File

import org.apache.logging.log4j.LogManager

/**
  * Takes in a HMM result
  *
  *
  * These files look like this:
  * <Header information>
  * <Lines of results that are good>
  * <Everything after, marked by the inclusion threshold>
  *
  * The lines of results that are good have a format described in HmmResultLine.
  *
  * Should be called as HmmResultParser.parseFile(<FileName>) which gives back a list of maps of each of the lines.
  */
object HmmResultParser {
  private val logger = LogManager.getLogger(getClass.getName)
  private val START_PARSING_INDICATOR = "------- ------ -----"
  private val STOP_PARSING_INDICATOR = "inclusion threshold"

  def parseFile(lines: Iterator[String]): Iterator[Map[String, String]] = {
    // Group 2 has everything after the start parsing indicator
    val result = lines.span(!_.contains(START_PARSING_INDICATOR))

    // Group 1 has everything prior to the stop parsing indicator
    val result_proteins = result._2.span(l => !l.contains(STOP_PARSING_INDICATOR) && !l.trim.isEmpty)

    // This means that the stop parsing indicator was never hit,
    // which means that there are no results.
    if (result_proteins._2.isEmpty) {
      logger.error("The reader read the whole iterator, indicating that the supplied " +
        "iterator was empty or had no sequences")
      return Iterator[Map[String, String]]()
    }

    /*
      Remove Start parsing indicator
     */
    if (result_proteins._1.hasNext) {
      result_proteins._1.next
    } else {
      logger.error(s"No lines found in result location.  Please check your iterator source.")
      return Iterator[Map[String, String]]()
    }

    // All the good lines, sent to parser, then returned as a map of FieldNames: Values
    result_proteins._1.map(HmmResultLine.parse)
  }

  def parseFile(openFile: File): Iterator[Map[String, String]] = {
    /*
      Note: If we are using an iterator here, we can't use .length to determine anything.
     */
    val hmmResultFile = scala.io.Source.fromFile(openFile)
    val result = parseFile(hmmResultFile.getLines())
    hmmResultFile.close()
    result
  }

  /*
  Each line contains these domains.

  File format is described by the following header:

   0        1      2       3        4      5       6    7  8         9 -> End (Given we split on empty strings)
  <E-value  score  bias    E-value  score  bias    exp  N  Sequence  Description>

  We get all the values except "exp" and "N"
   */
  object HmmResultLine {
    val fullSequence = "Full Sequence"
    val bestDomain = "Best 1 Domain"
    val E_VALUE_FULL_SEQUENCE = s"E-value $fullSequence"
    val E_VALUE_DOMAIN = s"E-value $bestDomain"
    val SCORE_FULL_SEQUENCE = s"score $fullSequence"
    val SCORE_DOMAIN = s"score $bestDomain"
    val BIAS_FULL_SEQUENCE = s"bias $fullSequence"
    val BIAS_DOMAIN = s"bias $bestDomain"
    val SEQUENCE_NAME = "Sequence Name"
    val DESCRIPTION = "Description"

    def parse(line: String): Map[String, String] = {
      // Get only values
      val lineValues = line.split(" ").filter(x => !x.equals(""))
      Map[String, String](
        E_VALUE_FULL_SEQUENCE -> lineValues(0),
        SCORE_FULL_SEQUENCE -> lineValues(1),
        BIAS_FULL_SEQUENCE -> lineValues(2),

        E_VALUE_DOMAIN -> lineValues(3),
        SCORE_DOMAIN -> lineValues(4),
        BIAS_DOMAIN -> lineValues(5),

        SEQUENCE_NAME -> lineValues(8),

        // Description might have spaces in it so we just collect the rest as the description
        DESCRIPTION -> lineValues.slice(9, lineValues.length).mkString(" ")
      )
    }
  }

}
