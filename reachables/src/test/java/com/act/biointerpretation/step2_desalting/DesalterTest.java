package com.act.biointerpretation.step2_desalting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DesalterTest {

  private final DesaltingROCorpus DESALTING_CORPUS_ROS = new DesaltingROCorpus();

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(Desalter.class);
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testDesalting() throws Exception {
    List<DesaltingRO> tests = DESALTING_CORPUS_ROS.getDesaltingROS().getRos();

    //Test all the things that should get cleaned for proper cleaning
    for (DesaltingRO ro : tests) {
      for (int i = 0; i < ro.getTestCases().size(); i++) {
        String input = ro.getTestCases().get(i).getInput();
        String expectedOutput = ro.getTestCases().get(i).getExpected();
        String name = ro.getTestCases().get(i).getLabel();

        Set<String> results = Desalter.desaltMolecule(input);
        assertTrue(name, results.size() == 1);

        String desaltedCompound = results.iterator().next();
        assertTrue(name, expectedOutput.equals(desaltedCompound));
      }
    }
  }

  @Test
  public void testDesaltingConstants() throws Exception {
    BufferedReader desaltConstantsReader = DESALTING_CORPUS_ROS.getDesalterConstantsReader();

    String inchi = null;
    while ((inchi = desaltConstantsReader.readLine()) != null) {
      Set<String> results = Desalter.desaltMolecule(inchi);
      assertTrue(results.size() == 1);
      String desaltedMolecule = results.iterator().next();
      assertTrue(inchi.equals(desaltedMolecule));
    }

    desaltConstantsReader.close();
  }
}
