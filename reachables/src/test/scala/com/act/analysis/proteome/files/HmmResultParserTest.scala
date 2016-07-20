package com.act.analysis.proteome.files

import org.scalatest._

object HmmResultParserTest extends FlatSpec with Matchers {

  "The HmmParser" should "return no value for the negative output file" in {
    val results =
      HmmResultParser.parseFile("com/act/analysis.proteome.files/output_result_negative_HmmResultParser.txt")

    results shouldNot be(null)
    results.size should be(0)
  }

  "The HmmParser" should "return all the lines above the cutoff line for the positive output file" in {
    val results =
      HmmResultParser.parseFile("com/act/analysis.proteome.files/output_result_positive_HmmResultParser.txt")

    results shouldNot be(null)
    results.size shouldNot be(0)
  }
}
