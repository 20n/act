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

import org.scalatest._

class HmmResultParserTest extends FlatSpec with Matchers {
  "The HmmParser" should "return no value for the negative output file" in {
    val results =
      HmmResultParser.parseFile(
        new File(getClass.getResource("/com/act/analysis.proteome.files/output_result_negative_HmmResultParser.txt").getFile)
      )

    results shouldNot be(null)
    results.size should be(0)
  }

  "The HmmParser" should "return all the lines above the cutoff line for the positive output file" in {
    val results =
      HmmResultParser.parseFile(
        new File(getClass.getResource("/com/act/analysis.proteome.files/output_result_positive_HmmResultParser.txt").getFile)
      )

    results shouldNot be(null)
    results.size shouldNot be(0)
  }

  "The HmmParser" should "trim any non protein values" in {
    val results =
      HmmResultParser.parseFile(
        new File(getClass.getResource("/com/act/analysis.proteome.files/output_result_positive_HmmResultParser.txt").getFile)
      ).toList

    results.head(HmmResultParser.HmmResultLine.SEQUENCE_NAME) should be("tr|A0A0A2K6V8|A0A0A2K6V8_PENEN")
    results.last(HmmResultParser.HmmResultLine.SEQUENCE_NAME) should be("tr|A0A0C1WNZ9|A0A0C1WNZ9_9CYAN")
  }
}
