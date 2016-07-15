package com.act.analysis.proteome.files

import java.io.File

object HmmResultParser {
  private val START_PARSING_INDICATOR = "------- ------ -----"
  private val STOP_PARSING_INDICATOR = "------ inclusion threshold ------"

  def parseFile(fileName: String): List[Map[String, String]] = {
    val openFile = new File(fileName)

    val lines = scala.io.Source.fromFile(openFile).getLines().toList

    // Group 2 has everything after the start parsing indicator
    val result = lines.span(!_.contains(START_PARSING_INDICATOR))
    // Group 1 has everything prior to the stop parsing indicator
    val skipHead = result._2.slice(1, result._2.length)

    val result_proteins = skipHead.span(!_.contains(STOP_PARSING_INDICATOR))

    // This means that the stop parsing indicator was never hit,
    // which means that there are no results.
    if (result_proteins._2.isEmpty) {
      return List[Map[String, String]]()
    }

    // All the lines
    result_proteins._1.map(HmmResultLine.parse _)
  }

  object HmmResultLine {
    val E_VALUE_FULL_SEQUENCE = s"E-value $fullSequence"
    val E_VALUE_DOMAIN = s"E-value $bestDomain"
    val SCORE_FULL_SEQUENCE = s"score $fullSequence"
    val SCORE_DOMAIN = s"score $bestDomain"
    val BIAS_FULL_SEQUENCE = s"bias $fullSequence"
    val BIAS_DOMAIN = s"bias $bestDomain"
    val SEQUENCE_NAME = "Sequence Name"
    val DESCRIPTION = "Description"
    private val fullSequence = "Full Sequence"
    private val bestDomain = "Best 1 Domain"

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
