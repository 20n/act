package com.act.analysis.proteome.files

import java.io.File

import org.scalatest._

class HmmResultParserTest extends FlatSpec with Matchers {
  private val INSTANCE_CLASS_LOADER: Class[_] = getClass

  "The HmmParser" should "return no value for the negative output file" in {
    val results =
      HmmResultParser.parseFile(new File(
        getClass.getResource("/com/act/analysis.proteome.files/output_result_negative_HmmResultParser.txt").getFile
      ))

    results shouldNot be(null)
    results.size should be(0)
  }

  "The HmmParser" should "return all the lines above the cutoff line for the positive output file" in {
    val results =
      HmmResultParser.parseFile(new File(
        getClass.getResource("/com/act/analysis.proteome.files/output_result_positive_HmmResultParser.txt").getFile
      ))

    results shouldNot be(null)
    results.size shouldNot be(0)
  }

  "The HmmParser" should "trim any non protein values" in {
    val results =
      HmmResultParser.parseFile(new File(
        getClass.getResource("/com/act/analysis.proteome.files/output_result_positive_HmmResultParser.txt").getFile
      ))

    results.head(HmmResultParser.HmmResultLine.SEQUENCE_NAME) should be("tr|A0A0A2K6V8|A0A0A2K6V8_PENEN")
    results.last(HmmResultParser.HmmResultLine.SEQUENCE_NAME) should be("tr|A0A0C1WNZ9|A0A0C1WNZ9_9CYAN")
  }
}
