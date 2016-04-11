package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.Utils.TestUtils;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ReactionDesalterTest {

  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ReactionDesalter.class);
    utilsObject = new TestUtils();
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testFakeReactionsArePreservedDuringDesaltingProcess() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] substrates = {1L, 2L, 3L};
    Long[] products = {4L, 5L, 6L};
    Integer[] substrateCoefficients = {1, 2, 3};
    Integer[] productCoefficients = {2, 3, 1};
    Boolean isRealInchi = false;

    // Group 1
    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);
    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    for (int i = 0; i < substrates.length; i++) {
      assertEquals(String.format("Input reaction substrate %d has correct coefficient set", substrates[i]),
          substrateCoefficients[i], testReactions.get(0).getSubstrateCoefficient(substrates[i]));
    }
    for (int i = 0; i < products.length; i++) {
      assertEquals(String.format("Input reaction product %d has correct coefficient set", products[i]),
          productCoefficients[i], testReactions.get(0).getProductCoefficient(products[i]));
    }

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, isRealInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, new Desalter());
    testReactionDesalter.run();

    assertEquals("Fake reactions should be written to the reaction DB", 2, mockAPI.getWrittenReactions().size());

    for (int i = 0; i < testReactions.size(); i++) {
      assertEquals("The reaction written to the DB should exactly match the input reaction",
          mockAPI.getWrittenReactions().get(i), testReactions.get(i));
    }
  }

  @Test
  public void testRealReactionsArePreservedDuringDesaltingProcess() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] substrates = {1L, 2L, 3L};
    Long[] products = {4L, 5L, 6L};
    Integer[] substrateCoefficients = {1, 2, 3};
    Integer[] productCoefficients = {2, 3, 1};
    String testChemicalInchiName = "test";
    Boolean isRealInchi = true;

    Desalter mockedDesalter = mock(Desalter.class);

    Set<String> testOuput = new HashSet<>();
    testOuput.add(testChemicalInchiName);
    when(mockedDesalter.desaltMolecule(anyString())).thenReturn(testOuput);

    // Group 1
    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);
    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    for (int i = 0; i < substrates.length; i++) {
      assertEquals(String.format("Input reaction substrate %d has correct coefficient set", substrates[i]),
          substrateCoefficients[i], testReactions.get(0).getSubstrateCoefficient(substrates[i]));
    }
    for (int i = 0; i < products.length; i++) {
      assertEquals(String.format("Input reaction product %d has correct coefficient set", products[i]),
          productCoefficients[i], testReactions.get(0).getProductCoefficient(products[i]));
    }

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, isRealInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, mockedDesalter);
    testReactionDesalter.run();

    assertEquals("Real reactions should be written to the reaction db", 2, mockAPI.getWrittenReactions().size());
    assertEquals("The first unique chemical should be written to the chemical db. All other duplicates should not " +
        "be written.", 1, mockAPI.getWrittenChemicals().size());

    for (int i = 0; i < testReactions.size(); i++) {
      assertEquals("The reaction written to the DB should exactly match the input reaction",
          mockAPI.getWrittenReactions().get(i), testReactions.get(i));
    }

    assertEquals("The chemical's name written to the DB should exactly match the intput chemical",
        mockAPI.getWrittenChemicals().get(1L).getInChI(), testChemicalInchiName);
  }
}
