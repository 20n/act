package com.act.biointerpretation.desalting;

import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.utils.TSVParser;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Note: to use the Chemaxon desalter, you'll need to have a Chemaxon license file installed in your home directory.
 * To do this, run (after connecting to the NAS):
 * $ /shared-data/3rdPartySoftware/Chemaxon/marvinbeans/bin/license [path to a valid license file]
 * This will copy the license to ~/.chemaxon/license.cxl, which the Chemaxon libraries will find automatically when
 * the license manager is invoked.
 */
public class DesalterTest {

  private final DesaltingROCorpus DESALTING_CORPUS_ROS = new DesaltingROCorpus();

  @Test
  public void testDesalting() throws Exception {
    List<DesaltingRO> tests = DESALTING_CORPUS_ROS.getDesaltingROS().getRos();

    Desalter desalter = new Desalter(new ReactionProjector());
    desalter.initReactors();

    //Test all the things that should get cleaned for proper cleaning
    for (DesaltingRO ro : tests) {
      for (int i = 0; i < ro.getTestCases().size(); i++) {
        String input = ro.getTestCases().get(i).getInput();
        String expectedOutput = ro.getTestCases().get(i).getExpected();
        String name = ro.getTestCases().get(i).getLabel();

        Map<String, Integer> results = desalter.desaltMolecule(input);
        assertNotNull(results);
        assertEquals(String.format("Desalting RO Test: %s", name), results.size(), 1);

        String desaltedCompound = results.keySet().iterator().next();
        assertEquals(String.format("Desalting RO Test: %s", name), expectedOutput, desaltedCompound);
      }
    }
  }

  @Test
  public void testDesaltingConstants() throws Exception {
    BufferedReader desaltConstantsReader = DESALTING_CORPUS_ROS.getDesalterConstantsReader();

    Desalter desalter = new Desalter(new ReactionProjector());
    desalter.initReactors();

    String inchi = null;
    while ((inchi = desaltConstantsReader.readLine()) != null) {

      Map<String, Integer> results = desalter.desaltMolecule(inchi);
      assertTrue(results.size() == 1);
      String desaltedMolecule = results.keySet().iterator().next();
      assertEquals(inchi, desaltedMolecule);
    }

    desaltConstantsReader.close();
  }

  @Test
  public void testDesaltingDetectsAndCountsRepeatedFragments() throws Exception {
    List<Pair<String, Map<String, Integer>>> testCases = new ArrayList<Pair<String, Map<String, Integer>>>() {{
      add(Pair.of( // Phenanthroline!
          "InChI=1S/2C12H8N2.2ClH.Ru/c2*1-3-9-5-6-10-4-2-8-14-12(10)11(9)13-7-1;;;/h2*1-8H;2*1H;/q2*-2;;;+9",
          new HashMap<String, Integer>() {{
            put("InChI=1S/C12H8N2/c1-3-9-5-6-10-4-2-8-14-12(10)11(9)13-7-1/h1-8H/q-2", 2);
          }}
      ));
      add(Pair.of( // Cyanide!
          "InChI=1S/12CN.2Fe.2H/c12*1-2;;;;/q12*-1;+2;+3;;",
          new HashMap<String, Integer>() {{
            put("InChI=1S/CN/c1-2/q-1", 12);
          }}
      ));
      add(Pair.of( // Bypyradine!
          "InChI=1S/2C10H10N2.2ClH.Ru/c2*1-3-7-11-9(5-1)10-6-2-4-8-12-10;;;/h2*1-10H;2*1H;/q2*-2;;;+8/p-2",
          new HashMap<String, Integer>() {{
            put("InChI=1S/C10H10N2/c1-3-7-11-9(5-1)10-6-2-4-8-12-10/h1-10H/q-2", 2);
          }}
      ));
      add(Pair.of( // Cyclopentadien!
          "InChI=1S/2C5H5.F6P.Fe/c2*1-2-4-5-3-1;1-7(2,3,4,5)6;/h2*1-5H;;/q3*-1;+3",
          new HashMap<String, Integer>() {{
            put("InChI=1S/C5H5/c1-2-4-5-3-1/h1-5H/q-1", 2);
          }}
      ));
      add(Pair.of( // Citrate!  (Bonus: multiple copper ions.)
          "InChI=1S/2C6H8O7.3Cu/c2*7-3(8)1-6(13,5(11)12)2-4(9)10;;;/h2*13H,1-2H2,(H,7,8)(H,9,10)(H,11,12);;;/q;;3*+2/p-6",
          new HashMap<String, Integer>() {{
            put("InChI=1S/C6H8O7/c7-3(8)1-6(13,5(11)12)2-4(9)10/h13H,1-2H2,(H,7,8)(H,9,10)(H,11,12)", 2);
          }}
      ));
    }};

    Desalter desalter = new Desalter(new ReactionProjector());
    desalter.initReactors();

    for (Pair<String, Map<String, Integer>> testCase : testCases) {
      String inchi = testCase.getLeft();
      Map<String, Integer> expectedFragmentCounts = testCase.getRight();
      Map<String, Integer> actual = desalter.desaltMolecule(inchi);
      assertEquals(String.format("Fragments and counts match for %s", inchi),
          expectedFragmentCounts, actual);
    }
  }

  /**
   * This test is odd in that it's a test of consistency rather than of correctness.  The input dataset is ~1000 InChIs
   * from the production DB that were run through both the current and previous implementations of the desalter and
   * found to be sufficiently equivalent in all but a few (now understood) cases.  The included InChIs were either
   * modified or unaltered by the desalter; complex InChIs are not considered at the moment.
   *
   * This test ensures that the set of InChIs that were used to evaluate the behavior of the desalter in its conversion
   * to Chemaxon's libraries are treated consistently as the desalter evolves.
   *
   * If this test fails, **do not panic.**  It is possible you've actually improved the desalter's behavior, so this
   * test may be telling you that your changes are doing the /right/ thing.  If the difference in InChIs (which you can
   * determine by using the ReactionDesalter on the list of test InChIs) looks good, update the data file to match your
   * new output.
   *
   * TODO: add some complex InChIs that can be split into components and/or desalted.
   *
   * TODO: if this test gets in the way of forward progress, remove it.
   *
   * @throws Exception
   */
  @Test
  public void testDesaltingOnKnownInChIs() throws Exception {
    InputStream testInChIsStream = getClass().getResourceAsStream("desalter_test_cases.txt");
    TSVParser parser = new TSVParser();
    parser.parse(testInChIsStream);

    Desalter desalter = new Desalter(new ReactionProjector());
    desalter.initReactors();

    int i = 0;
    for (Map<String, String> row : parser.getResults()) {
      i++;
      String input = row.get("input");
      String expected = row.get("expected_output");

      Map<String, Integer> results = desalter.desaltMolecule(input);
      assertNotNull(String.format("Case %d: desalter results are not null", i), results);
      assertEquals(String.format("Case %d: only one desalted molecule is produced", i), 1, results.size());
      assertEquals(String.format("Case %d: desalter produces expected results", i),
          expected, results.keySet().iterator().next());
    }
  }
}
