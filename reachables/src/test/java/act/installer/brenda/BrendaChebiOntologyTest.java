package act.installer.brenda;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


import static org.junit.Assert.assertEquals;

/**
 * Created by tom on 10/3/16.
 */
public class BrendaChebiOntologyTest {


  // Test printing capacities
  // Test

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

  private HashSet<act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase> InchiFormattingTestCases = new HashSet<>();

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi1 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Perfect world example.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi2 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Trailing whitespaces.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2       ",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2       ",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi3 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Whitespace in the middle of InChI representation.",
      "| InChI=1/C5H8O2/c6-5-3-1-2-4 -7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4 -7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi4 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Standard InChI prefix.",
      "| StdInChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1S/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi5 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Spaces around equal sign.",
      "| InChI = 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI = 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi6 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Space after equal sign.",
      "| InChI= 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI= 1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi7 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Space before equal sign.",
      "| InChI =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi8 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Many spaces after equal sign.",
      "| InChI=     1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=     1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase inchi9 = new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase(
      "Many spaces before equal sign.",
      "| InChI              =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI              =1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2",
      "InChI=1/C5H8O2/c6-5-3-1-2-4-7-5/h1-4H2");

  private static final act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase[] INCHI_FORMATTING_TEST_CASES_LIST =
      new act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase[] {
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

  private static final Set<act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase> INCHI_FORMATTING_TEST_CASES = new HashSet<>(
      Arrays.asList(INCHI_FORMATTING_TEST_CASES_LIST));

  @Test
  public void testFormatInchiString() throws Exception {
    ImportantChemicalsWikipedia importantChemicalsWikipedia = new ImportantChemicalsWikipedia();


    for (act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase testcase : INCHI_FORMATTING_TEST_CASES) {
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

    for (act.installer.wikipedia.ImportantChemicalsWikipediaTest.InchiFormattingTestCase testcase : INCHI_FORMATTING_TEST_CASES) {
      assertEquals(
          "Testing case: " + testcase.getDescription(),
          importantChemicalsWikipedia.extractInchiFromLine(testcase.getExtractedLine()),
          testcase.getMatchedInchi()
      );
    }
  }
}
