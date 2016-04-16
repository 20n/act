package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.test.util.TestUtils;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  public void testFakeReactionShouldBeWrittenToWriteDBInTheDesaltingProcess() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] substrates = {1L, 2L, 3L};
    Long[] products = {4L, 5L, 6L};
    Integer[] substrateCoefficients = {1, 2, 3};
    Integer[] productCoefficients = {2, 3, 1};

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
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, new HashMap<>());

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    assertEquals("Two similar fake reactions should be merged into one in the write DB", 2,
        mockAPI.getWrittenReactions().size());

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      assertEquals("The reaction written to the DB should exactly match the input reaction",
          mockAPI.getWrittenReactions().get(i), testReactions.get(i));
    }
  }

  @Test
  public void testSubstratesThatMapsToTheSameDesaltedSubstrateShouldBeWrittenToTheDBAsTwoEntriess() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/CH2O2.K/c2-1-3;/h1H,(H,2,3);/q;+1/p-1");
    idToInchi.put(2L, "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");

    Integer[] substrateCoefficients = {1};
    Integer[] productCoefficients = {2};

    Long[] substrates1 = {1L};
    Long[] substrates2 = {2L};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients, productCoefficients, true);

    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates2, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    assertEquals("Similar pre-desalted substrates should be written as two entries", 2,
        mockAPI.getWrittenReactions().size());

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      for (Long substrateCoefficient : mockAPI.getWrittenReactions().get(i).getSubstrateIdsOfSubstrateCoefficients()) {
        Integer val = substrateCoefficient.intValue();
        assertEquals("Make sure the substrate coefficients are preserved during the migration", val, substrateCoefficients[0]);
      }

      for (Long productCoefficient : mockAPI.getWrittenReactions().get(i).getProductIdsOfProductCoefficients()) {
        Integer val = productCoefficient.intValue();
        assertEquals("Make sure the product coefficients are preserved during the migration", val, productCoefficients[0]);
      }
    }

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      Long rnxSubstrateId = mockAPI.getWrittenReactions().get(i).getSubstrates()[0];
      assertEquals("The reaction written to the write DB should have the desalted inchi name",
          mockAPI.getWrittenChemicals().get(rnxSubstrateId).getInChI(), "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)");
    }
  }

  @Test
  public void testSubstratesThatMapsToTheSameDesaltedSubstrateButWithDifferentSubtrateCoefficientsShouldBeWrittenAsTwoEntries()
      throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();
    // The inchi below, once desalted, maps to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/CH2O2.K/c2-1-3;/h1H,(H,2,3);/q;+1/p-1");

    Long[] substrates = {1L};

    Integer[] substrateCoefficients = {1};
    Integer[] substrateCoefficients1 = {2};

    Integer[] productCoefficients = {2};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients1, productCoefficients, true);

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    assertEquals("Even if the same pre-desalted chemical mapped to the same desalter chemical," +
        "there should be 2 entries in the write DB when the substrate coefficients are not the same",
        2, mockAPI.getWrittenReactions().size());

    Long rnxSubstrateId1 = mockAPI.getWrittenReactions().get(0).getSubstrates()[0];
    Long rnxSubstrateId2 = mockAPI.getWrittenReactions().get(1).getSubstrates()[0];

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      for (Long substrateCoefficient : mockAPI.getWrittenReactions().get(i).getSubstrateIdsOfSubstrateCoefficients()) {
        Integer val = substrateCoefficient.intValue();
        assertEquals("Make sure the substrate coefficients are preserved during the migration", val, substrateCoefficients[0]);
      }

      for (Long productCoefficient : mockAPI.getWrittenReactions().get(i).getProductIdsOfProductCoefficients()) {
        Integer val = productCoefficient.intValue();
        assertEquals("Make sure the product coefficients are preserved during the migration", val, productCoefficients[0]);
      }
    }

    assertEquals("The reaction written to the write DB should have the desalted inchi name",
        mockAPI.getWrittenChemicals().get(rnxSubstrateId1).getInChI(), "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)");
    assertEquals("The reaction written to the write DB should have the desalted inchi name",
        mockAPI.getWrittenChemicals().get(rnxSubstrateId2).getInChI(), "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)");
  }
}
