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

package act.installer.wikipedia;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ImportantChemicalsWikipediaTest {

  public static class InchiFormattingTestCase {

    private String description;
    private String extractedLine;
    private String matchedInchi;
    private String expectedInchi;

    public InchiFormattingTestCase(
        String description, String extractedLine, String matchedInchi, String expectedInchi) {
      this.description = description;
      this.extractedLine = extractedLine;
      this.matchedInchi = matchedInchi;
      this.expectedInchi = expectedInchi;
    }

    public String getDescription() {
      return description;
    }

    public String getExtractedLine() {
      return extractedLine;
    }

    public String getMatchedInchi() {
      return matchedInchi;
    }

    public String getExpectedInchi() {
      return expectedInchi;
    }
  }

  private HashSet<InchiFormattingTestCase> InchiFormattingTestCases = new HashSet<>();

  private static final InchiFormattingTestCase inchi1 = new InchiFormattingTestCase(
      "Perfect world example.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi2 = new InchiFormattingTestCase(
      "Trailing whitespaces.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2       ",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2       ",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi3 = new InchiFormattingTestCase(
      "Whitespace in the middle of InChI representation.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4 -7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4 -7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi4 = new InchiFormattingTestCase(
      "Standard InChI prefix.",
      "| StdInChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi5 = new InchiFormattingTestCase(
      "Spaces around equal sign.",
      "| InChI = 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI = 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi6 = new InchiFormattingTestCase(
      "Space after equal sign.",
      "| InChI= 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI= 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi7 = new InchiFormattingTestCase(
      "Space before equal sign.",
      "| InChI =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi8 = new InchiFormattingTestCase(
      "Many spaces after equal sign.",
      "| InChI=     1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=     1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase inchi9 = new InchiFormattingTestCase(
      "Many spaces before equal sign.",
      "| InChI              =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI              =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final InchiFormattingTestCase[] INCHI_FORMATTING_TEST_CASES_LIST =
      new InchiFormattingTestCase[] {
          inchi1,
          inchi2,
          inchi3,
          inchi4,
          inchi5,
          inchi6,
          inchi7,
          inchi8,
          inchi9
      };

  private static final Set<InchiFormattingTestCase> INCHI_FORMATTING_TEST_CASES = new HashSet<>(
      Arrays.asList(INCHI_FORMATTING_TEST_CASES_LIST));

  @Test
  public void testFormatInchiString() throws Exception {
    ImportantChemicalsWikipedia importantChemicalsWikipedia = new ImportantChemicalsWikipedia();


    for (InchiFormattingTestCase testcase : INCHI_FORMATTING_TEST_CASES) {
      assertEquals(
          "Testing case: " + testcase.getDescription(),
          importantChemicalsWikipedia.formatInchiString(testcase.getMatchedInchi()),
          testcase.getExpectedInchi()
      );
    }
  }

  @Test
  public void testExtractInchiFromLine() throws Exception {
    ImportantChemicalsWikipedia importantChemicalsWikipedia = new ImportantChemicalsWikipedia();

    for (InchiFormattingTestCase testcase : INCHI_FORMATTING_TEST_CASES) {
      assertEquals(
          "Testing case: " + testcase.getDescription(),
          importantChemicalsWikipedia.extractInchiFromLine(testcase.getExtractedLine()),
          testcase.getMatchedInchi()
      );
    }
  }
}

