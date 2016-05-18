package act.installer.wikipedia;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class WikipediaChemicalTest {

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

  @Test
  public void testFormatInchiString() throws Exception {
    WikipediaChemical wikipediaChemical = new WikipediaChemical();
    InchiFormattingTestCases.add(inchi1);
    InchiFormattingTestCases.add(inchi2);
    InchiFormattingTestCases.add(inchi3);
    InchiFormattingTestCases.add(inchi4);
    InchiFormattingTestCases.add(inchi5);
    InchiFormattingTestCases.add(inchi6);
    InchiFormattingTestCases.add(inchi7);

    for (InchiFormattingTestCase testcase : InchiFormattingTestCases) {
      assertEquals(
          "Testing case: " + testcase.getDescription(),
          wikipediaChemical.formatInchiString(testcase.getMatchedInchi()),
          testcase.getExpectedInchi()
      );
    }
  }

  @Test
  public void testExtractInchiFromLine() throws Exception {
    WikipediaChemical wikipediaChemical = new WikipediaChemical();
    InchiFormattingTestCases.add(inchi1);
    InchiFormattingTestCases.add(inchi2);
    InchiFormattingTestCases.add(inchi3);
    InchiFormattingTestCases.add(inchi4);
    InchiFormattingTestCases.add(inchi5);
    InchiFormattingTestCases.add(inchi6);
    InchiFormattingTestCases.add(inchi7);

    for (InchiFormattingTestCase testcase : InchiFormattingTestCases) {
      assertEquals(
          "Testing case: " + testcase.getDescription(),
          wikipediaChemical.extractInchiFromLine(testcase.getExtractedLine()),
          testcase.getMatchedInchi()
      );
    }
  }
}

