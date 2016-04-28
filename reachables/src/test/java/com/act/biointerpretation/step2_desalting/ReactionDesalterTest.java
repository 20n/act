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
import static org.junit.Assert.assertNotNull;

public class ReactionDesalterTest {

  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ReactionDesalter.class);
    utilsObject = new TestUtils();
  }

  @After
  public void tearDown() throws Exception {}

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
          testReactions.get(i), mockAPI.getWrittenReactions().get(i));
    }
  }

  @Test
  public void testSubstratesThatMapsToTheSameDesaltedSubstrateShouldBeWrittenToTheDBAsTwoEntries() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/CH2O2.K/c2-1-3;/h1H,(H,2,3);/q;+1/p-1");
    idToInchi.put(2L, "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");

    Integer[] substrateCoefficients = {3};
    Integer[] productCoefficients = {5};

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
      Reaction rxn = mockAPI.getWrittenReactions().get(i);
      for (Long substrateId : rxn.getSubstrateIdsOfSubstrateCoefficients()) {
        assertEquals("Make sure the substrate coefficients are preserved during the migration",
            substrateCoefficients[0], rxn.getSubstrateCoefficient(substrateId));
      }

      for (Long productId : rxn.getProductIdsOfProductCoefficients()) {
        assertEquals("Make sure the product coefficients are preserved during the migration",
            productCoefficients[0], rxn.getProductCoefficient(productId));
      }
    }

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      Long rnxSubstrateId = mockAPI.getWrittenReactions().get(i).getSubstrates()[0];
      assertEquals("The reaction written to the write DB should have the desalted inchi name",
          "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", mockAPI.getWrittenChemicals().get(rnxSubstrateId).getInChI());
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

    Integer[] substrateCoefficients = {3};
    Integer[] substrateCoefficients1 = {5};

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

    // Hacky way of mapping reactions to their respective subs/prods coefficients.  TODO: do this better!
    Integer[][] rxnSubstrateCoefficients = new Integer[][] {substrateCoefficients, substrateCoefficients1};
    Integer[][] rxnProductCoefficients = new Integer[][] {productCoefficients, productCoefficients};

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      Reaction rxn = mockAPI.getWrittenReactions().get(i);
      for (Long substrateId : rxn.getSubstrateIdsOfSubstrateCoefficients()) {
        assertEquals("Make sure the substrate coefficients are preserved during the migration",
            rxnSubstrateCoefficients[i][0], rxn.getSubstrateCoefficient(substrateId));
      }

      for (Long productId : rxn.getProductIdsOfProductCoefficients()) {
        assertEquals("Make sure the product coefficients are preserved during the migration",
            rxnProductCoefficients[i][0], rxn.getProductCoefficient(productId));
      }
    }

    Long rnxSubstrateId1 = mockAPI.getWrittenReactions().get(0).getSubstrates()[0];
    Long rnxSubstrateId2 = mockAPI.getWrittenReactions().get(1).getSubstrates()[0];
    assertEquals("The reaction written to the write DB should have the desalted inchi",
        "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", mockAPI.getWrittenChemicals().get(rnxSubstrateId1).getInChI());
    assertEquals("The reaction written to the write DB should have the desalted inchi",
        "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", mockAPI.getWrittenChemicals().get(rnxSubstrateId2).getInChI());
  }

  @Test
  public void testDuplicateDesaltedChemicalsInSubstratesAndProductsMapToTheCorrectSubstratesAndProductsAndCoefficientsAreNotNull() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/C36H36N4O2.Fe/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29;/h9-10,15-18H,1-2,11-14H2,3-8H3;/q-2;+7/b29-15-,30-15-,31-16-,32-17-,33-16-,34-17-,35-18-,36-18-;");
    idToInchi.put(2L, "InChI=1S/6CN.Fe.K.H/c6*1-2;;;/q;;;;;;;+1;");
    idToInchi.put(3L, "InChI=1S/p+1");

    idToInchi.put(4L, "InChI=1S/H2O/h1H2");
    idToInchi.put(5L, "InChI=1S/C36H36N4O2.Fe/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29;/h9-10,15-18H,1-2,11-14H2,3-8H3;/q-2;+6/b29-15-,30-15-,31-16-,32-17-,33-16-,34-17-,35-18-,36-18-;");
    idToInchi.put(6L, "InChI=1S/6CN.Fe.3K/c6*1-2;;;;/q;;;;;;-3;3*+1");

    Integer[] substrateCoefficients = {2, 1, 1};
    Integer[] productCoefficients = {1, 3, 1};

    Long[] substrates = {4L, 5L, 6L};
    Long[] products = {1L, 2L, 3L};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    // substrates comparison
    assertEquals(mockAPI.getWrittenReactions().get(0).getSubstrateCoefficient(mockAPI.getWrittenReactions().get(0).getSubstrates()[0]).intValue(), 2);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrates()[0]).getInChI(), "InChI=1S/H2O/h1H2");

    assertEquals(mockAPI.getWrittenReactions().get(0).getSubstrateCoefficient(mockAPI.getWrittenReactions().get(0).getSubstrates()[1]).intValue(), 1);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrates()[1]).getInChI(), "InChI=1S/C36H36N4O2/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29/h9-10,15-18H,1-2,11-14H2,3-8H3/q-2/b29-15-,30-15?,31-16?,32-17-,33-16-,34-17?,35-18-,36-18?");

    assertEquals(mockAPI.getWrittenReactions().get(0).getSubstrateCoefficient(mockAPI.getWrittenReactions().get(0).getSubstrates()[2]).intValue(), 1);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrates()[2]).getInChI(), "InChI=1S/CN/c1-2");

    // products comparison
    assertEquals(mockAPI.getWrittenReactions().get(0).getProductCoefficient(mockAPI.getWrittenReactions().get(0).getProducts()[0]).intValue(), 1);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getProducts()[0]).getInChI(), "InChI=1S/C36H36N4O2/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29/h9-10,15-18H,1-2,11-14H2,3-8H3/q-2/b29-15-,30-15?,31-16?,32-17-,33-16-,34-17?,35-18-,36-18?");

    assertEquals(mockAPI.getWrittenReactions().get(0).getProductCoefficient(mockAPI.getWrittenReactions().get(0).getProducts()[1]).intValue(), 3);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getProducts()[1]).getInChI(), "InChI=1S/CN/c1-2");

    assertEquals(mockAPI.getWrittenReactions().get(0).getProductCoefficient(mockAPI.getWrittenReactions().get(0).getProducts()[2]).intValue(), 1);
    assertEquals(mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getProducts()[2]).getInChI(), "InChI=1S/p+1");

  }

  @Test
  public void testSubstratesThatMapsToTheSameDesaltedSubstrateHaveTheirCoefficientsMerged() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/CH2O2.K/c2-1-3;/h1H,(H,2,3);/q;+1/p-1");
    idToInchi.put(2L, "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");

    Integer[] substrateCoefficients = {3, 5};
    Integer[] productCoefficients = {5};

    Long[] substrates1 = {1L, 2L};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    assertEquals("One desalted reaction should be written", 1,
        mockAPI.getWrittenReactions().size());

    Reaction rxn = mockAPI.getWrittenReactions().get(0);
    for (Long substrateId : rxn.getSubstrateIdsOfSubstrateCoefficients()) {
      assertEquals("Desalted substrate coefficients should have been added together",
          Integer.valueOf(substrateCoefficients[0] + substrateCoefficients[1]),
          rxn.getSubstrateCoefficient(substrateId));
    }

    for (Long productId : rxn.getProductIdsOfProductCoefficients()) {
      assertEquals("Product coefficients should remain unchanged",
          productCoefficients[0], rxn.getProductCoefficient(productId));
    }

    Long rnxSubstrateId = rxn.getSubstrates()[0];
    assertEquals("The reaction written to the write DB should have the desalted inchi name",
        "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", mockAPI.getWrittenChemicals().get(rnxSubstrateId).getInChI());
  }

  @Test
  public void testSubstratesWithNullCoefficientsKeepThemEvenThroughAMerge() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/CH2O2.K/c2-1-3;/h1H,(H,2,3);/q;+1/p-1");
    idToInchi.put(2L, "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");

    Integer[] substrateCoefficients = {3, null}; // null + integer -> null when substrates/products merge
    Integer[] productCoefficients = {5};

    Long[] substrates1 = {1L, 2L};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    Desalter desalter = new Desalter();
    desalter.initReactors();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI, desalter);
    testReactionDesalter.run();

    assertEquals("One desalted reaction should be written", 1,
        mockAPI.getWrittenReactions().size());

    Reaction rxn = mockAPI.getWrittenReactions().get(0);
    for (Long substrateId : rxn.getSubstrateIdsOfSubstrateCoefficients()) {
      assertEquals("Desalted substrate coefficients should be null",
          null, rxn.getSubstrateCoefficient(substrateId));
    }

    for (Long productId : rxn.getProductIdsOfProductCoefficients()) {
      assertEquals("Product coefficients should remain unchanged",
          productCoefficients[0], rxn.getProductCoefficient(productId));
    }

    Long rnxSubstrateId = rxn.getSubstrates()[0];
    assertEquals("The reaction written to the write DB should have the desalted inchi name",
        "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", mockAPI.getWrittenChemicals().get(rnxSubstrateId).getInChI());
  }
}
