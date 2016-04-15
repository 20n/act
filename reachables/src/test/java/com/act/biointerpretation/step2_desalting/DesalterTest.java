package com.act.biointerpretation.step2_desalting;

import com.act.utils.TSVParser;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    Desalter desalter = new Desalter();
    desalter.initReactors();

    //Test all the things that should get cleaned for proper cleaning
    for (DesaltingRO ro : tests) {
      for (int i = 0; i < ro.getTestCases().size(); i++) {
        String input = ro.getTestCases().get(i).getInput();
        String expectedOutput = ro.getTestCases().get(i).getExpected();
        String name = ro.getTestCases().get(i).getLabel();

        Set<String> results = desalter.desaltMolecule(input);
        assertNotNull(results);
        assertEquals(String.format("Desalting RO Test: %s", name), results.size(), 1);

        String desaltedCompound = results.iterator().next();
        assertEquals(String.format("Desalting RO Test: %s", name), expectedOutput, desaltedCompound);
      }
    }
  }

  @Test
  public void testDesaltingConstants() throws Exception {
    BufferedReader desaltConstantsReader = DESALTING_CORPUS_ROS.getDesalterConstantsReader();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    String inchi = null;
    while ((inchi = desaltConstantsReader.readLine()) != null) {

      Set<String> results = desalter.desaltMolecule(inchi);
      assertTrue(results.size() == 1);
      String desaltedMolecule = results.iterator().next();
      assertEquals(inchi, desaltedMolecule);
    }

    desaltConstantsReader.close();
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
    File testInChIsFile = new File(getClass().getResource("desalter_test_cases.txt").getFile());
    TSVParser parser = new TSVParser();
    parser.parse(testInChIsFile);

    Desalter desalter = new Desalter();
    desalter.initReactors();

    int i = 0;
    for (Map<String, String> row : parser.getResults()) {
      i++;
      String input = row.get("input");
      String expected = row.get("expected_output");

      Set<String> results = desalter.desaltMolecule(input);
      assertNotNull(String.format("Case %d: desalter results are not null", i), results);
      assertEquals(String.format("Case %d: only one desalted molecule is produced", i), 1, results.size());
      assertEquals(String.format("Case %d: desalter produces expected results", i), expected, results.iterator().next());
    }
  }
}
