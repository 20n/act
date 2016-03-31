package com.act.biointerpretation.step2_desalting;

import org.junit.Test;
import java.io.BufferedReader;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

        System.out.format("Input inchi: %s\n", input);

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
      assertTrue(inchi.equals(desaltedMolecule));
    }

    desaltConstantsReader.close();
  }
}
