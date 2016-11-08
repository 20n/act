package act.installer.brenda;

import act.installer.wikipedia.ImportantChemicalsWikipedia;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


import static org.junit.Assert.assertEquals;


public class BrendaChebiOntologyTest {


  private BrendaChebiOntology.ChebiOntology buildTestCase(String id) {
    String chebiId = String.format("CHEBI:%s", id);
    String term  = String.format("term%s", id);
    String definition = String.format("definition%s", id);
    return new BrendaChebiOntology.ChebiOntology(chebiId, term, definition);
  }

  @Test
  /**
   * Map<ChebiOntology, Set<ChebiOntology>> getApplicationToMainApplicationsMap(
   Map<String, ChebiOntology> ontologyMap,
   Map<ChebiOntology, Set<ChebiOntology>> isSubtypeOfRelationships)
   */
  public void testApplicationToMainApplicationMapping() {

    List<Integer> range = IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
    List<BrendaChebiOntology.ChebiOntology> ontologies = range.stream().map(id -> buildTestCase(id.toString())).collect(Collectors.toList());
    Map<String, BrendaChebiOntology.ChebiOntology> ontologyMap = new HashMap<>();
    ontologies.forEach(o -> ontologyMap.put(o.getChebiId(), o));

    Map<BrendaChebiOntology.ChebiOntology, Set<BrendaChebiOntology.ChebiOntology>> isSubtypeOfRelationships = new HashMap<>();

    //         1 -> main application ontology
    //        / \
    //       2   3 -> these are main applications
    //      /     \
    //     4       5 -> these are sub applications
    //      \     / \
    //       \   /   6 -> another level of sub-applications
    //        \ /     \
    //         7       8 -> chemicals
    // Question: can there be nested chemicals?

    // 2 and 3 are main applications
    Set<BrendaChebiOntology.ChebiOntology> mainApplications = new HashSet<>();
    mainApplications.add(ontologies.get(1));
    mainApplications.add(ontologies.get(2));
    // 1 is the main application id
    isSubtypeOfRelationships.put(ontologies.get(0), mainApplications);

    // 4, 5 and 6 are sub applications. 4 is sub-application of 2 and 5 is sub-application of 3.
    isSubtypeOfRelationships.put(ontologies.get(1), Collections.singleton(ontologies.get(3)));
    isSubtypeOfRelationships.put(ontologies.get(2), Collections.singleton(ontologies.get(4)));
    isSubtypeOfRelationships.put(ontologies.get(4), Collections.singleton(ontologies.get(5)));

    // 7 and 8 are chemicals with respective applications 4 and 6
    isSubtypeOfRelationships.put(ontologies.get(3), Collections.singleton(ontologies.get(6)));
    isSubtypeOfRelationships.put(ontologies.get(4), Collections.singleton(ontologies.get(6)));
    isSubtypeOfRelationships.put(ontologies.get(5), Collections.singleton(ontologies.get(7)));

    // Expected map is:
    Map<BrendaChebiOntology.ChebiOntology, Set<BrendaChebiOntology.ChebiOntology>> applicationToMainApplicationMap = new HashMap<>();
    applicationToMainApplicationMap.put(ontologies.get(1), Collections.singleton(ontologies.get(1)));
    applicationToMainApplicationMap.put(ontologies.get(2), Collections.singleton(ontologies.get(2)));
    applicationToMainApplicationMap.put(ontologies.get(3), Collections.singleton(ontologies.get(1)));
    applicationToMainApplicationMap.put(ontologies.get(4), Collections.singleton(ontologies.get(2)));
    applicationToMainApplicationMap.put(ontologies.get(5), Collections.singleton(ontologies.get(2)));
    applicationToMainApplicationMap.put(ontologies.get(6), new HashSet<BrendaChebiOntology.ChebiOntology>() {{
      add(ontologies.get(1));
      add(ontologies.get(2));
    }});
    applicationToMainApplicationMap.put(ontologies.get(7), Collections.singleton(ontologies.get(2)));

    assertEquals(applicationToMainApplicationMap, BrendaChebiOntology.getApplicationToMainApplicationsMap(ontologyMap, isSubtypeOfRelationships, "CHEBI:1"));
  }



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
