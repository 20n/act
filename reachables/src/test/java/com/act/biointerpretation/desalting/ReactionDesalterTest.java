package com.act.biointerpretation.desalting;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
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

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
    testReactionDesalter.run();

    assertEquals("Two similar fake reactions should be merged into one in the write DB", 2,
        mockAPI.getWrittenReactions().size());

    for (int i = 0; i < mockAPI.getWrittenReactions().size(); i++) {
      assertEquals("The reaction written to the DB should exactly match the input reaction",
          testReactions.get(i), mockAPI.getWrittenReactions().get(i));
    }
  }

  @Test
  public void testSubstratesThatMapToTheSameDesaltedSubstrateShouldBeWrittenToTheDBAsTwoEntries() throws Exception {
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

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
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

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
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

    Map<Long, String> idToInchi = new HashMap<Long, String>() {{
      // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
      put(1L, "InChI=1S/C36H36N4O2.Fe/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29;/h9-10,15-18H,1-2,11-14H2,3-8H3;/q-2;+7/b29-15-,30-15-,31-16-,32-17-,33-16-,34-17-,35-18-,36-18-;");
      put(2L, "InChI=1S/6CN.Fe.K.H/c6*1-2;;;/q;;;;;;;+1;");
      put(3L, "InChI=1S/p+1");

      put(4L, "InChI=1S/H2O/h1H2");
      put(5L, "InChI=1S/C36H36N4O2.Fe/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29;/h9-10,15-18H,1-2,11-14H2,3-8H3;/q-2;+6/b29-15-,30-15-,31-16-,32-17-,33-16-,34-17-,35-18-,36-18-;");
      put(6L, "InChI=1S/6CN.Fe.3K/c6*1-2;;;;/q;;;;;;-3;3*+1");
    }};

    Map<Long, String> idToDesaltedInchi =  new HashMap<Long, String>() {{
      put(1L, "InChI=1S/C36H36N4O2/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29/h9-10,15-18H,1-2,11-14H2,3-8H3/q-2/b29-15-,30-15?,31-16?,32-17-,33-16-,34-17?,35-18-,36-18?");
      put(2L, "InChI=1S/CN/c1-2");
      put(3L, "InChI=1S/p+1");

      put(4L, "InChI=1S/H2O/h1H2");
      put(5L, "InChI=1S/C36H36N4O2/c1-9-25-21(5)29-15-30-23(7)27(13-11-19(3)41)35(39-30)18-36-28(14-12-20(4)42)24(8)32(40-36)17-34-26(10-2)22(6)31(38-34)16-33(25)37-29/h9-10,15-18H,1-2,11-14H2,3-8H3/q-2/b29-15-,30-15?,31-16?,32-17-,33-16-,34-17?,35-18-,36-18?");
      put(6L, "InChI=1S/CN/c1-2");
    }};

    Integer[] substrateCoefficients = {2, 17, 19};
    Integer[] productCoefficients = {1000, 3, 91};

    Long[] substrates = {4L, 5L, 6L};
    Long[] products = {1L, 2L, 3L};

    Integer[] expectedSubstrateCoefficients = {2, 17, 19 * 6};
    Integer[] expectedProductCoefficients = {1000, 3 * 6, 91};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
    testReactionDesalter.run();

    // substrates comparison
    Reaction rxn = mockAPI.getWrittenReactions().get(0);
    assertEquals("Substrate 1 has correct coefficient", expectedSubstrateCoefficients[0], rxn.getSubstrateCoefficient(rxn.getSubstrates()[0]));
    assertEquals("Substrate 1 has correct inchi", idToDesaltedInchi.get(4L), mockAPI.getWrittenChemicals().get(rxn.getSubstrates()[0]).getInChI());

    assertEquals("Substrate 2 has correct coefficient", expectedSubstrateCoefficients[1], rxn.getSubstrateCoefficient(rxn.getSubstrates()[1]));
    assertEquals("Substrate 3 has correct inchi", idToDesaltedInchi.get(5L), mockAPI.getWrittenChemicals().get(rxn.getSubstrates()[1]).getInChI());

    assertEquals("Substrate 3 has correct coefficient", expectedSubstrateCoefficients[2], rxn.getSubstrateCoefficient(rxn.getSubstrates()[2]));
    assertEquals("Substrate 3 has correct inchi", idToDesaltedInchi.get(6L), mockAPI.getWrittenChemicals().get(rxn.getSubstrates()[2]).getInChI());

    // products comparison
    assertEquals("Product 1 has correct coefficient", expectedProductCoefficients[0], rxn.getProductCoefficient(rxn.getProducts()[0]).intValue(), 1);
    assertEquals("Product 1 has correct inchi", idToDesaltedInchi.get(1L), mockAPI.getWrittenChemicals().get(rxn.getProducts()[0]).getInChI());

    assertEquals("Product 2 has correct coefficient", expectedProductCoefficients[1], rxn.getProductCoefficient(rxn.getProducts()[1]).intValue(), 3);
    assertEquals("Product 1 has correct inchi", idToDesaltedInchi.get(2L), mockAPI.getWrittenChemicals().get(rxn.getProducts()[1]).getInChI());

    assertEquals("Product 3 has correct coefficient", expectedProductCoefficients[2], rxn.getProductCoefficient(rxn.getProducts()[2]).intValue(), 1);
    assertEquals("Product 1 has correct inchi", idToDesaltedInchi.get(3L), mockAPI.getWrittenChemicals().get(rxn.getProducts()[2]).getInChI());

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

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
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

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
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

  @Test
  public void testCoefficientsAreMultipliedByTheNumberOfDesaltedFragments() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/2C12H8N2.2ClH.Ru/c2*1-3-9-5-6-10-4-2-8-14-12(10)11(9)13-7-1;;;/h2*1-8H;2*1H;/q2*-2;;;+9");
    idToInchi.put(2L, "InChI=1S/3C14H12N2.F6P.Ru/c3*1-9-3-4-12-11(7-9)8-16-14-13(12)10(2)5-6-15-14;1-7(2,3,4,5)6;/h3*3-8H,1-2H3;;/q;;;-1;+3");

    String expectedDesaltingInchi1 = "InChI=1S/C12H8N2/c1-3-9-5-6-10-4-2-8-14-12(10)11(9)13-7-1/h1-8H/q-2";
    String expectedDesaltingInchi2 = "InChI=1S/C14H12N2/c1-9-3-4-12-11(7-9)8-16-14-13(12)10(2)5-6-15-14/h3-8H,1-2H3";

    Long[] substrates1 = {1L};
    Long[] products = {2L};

    Integer[] substrateCoefficients = {3};
    Integer[] productCoefficients = {5};

    Integer[] expectedSubstrateCoefficients = {3 * 2};
    Integer[] expectedProductCoefficients = {5 * 3};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
    testReactionDesalter.run();

    assertEquals("One desalted reaction should be written", 1,
        mockAPI.getWrittenReactions().size());

    Reaction rxn = mockAPI.getWrittenReactions().get(0);

    Long[] newSubstrates = rxn.getSubstrates();
    assertEquals("One substrate remains after desalting", 1, newSubstrates.length);
    assertEquals("Single substrate has expected inchi", expectedDesaltingInchi1,
        mockAPI.getWrittenChemicals().get(newSubstrates[0]).getInChI());
    assertEquals("Single substrate has expected coefficient", expectedSubstrateCoefficients[0],
        rxn.getSubstrateCoefficient(newSubstrates[0]));

    Long[] newProducts = rxn.getProducts();
    assertEquals("One Product remains after desalting", 1, newProducts.length);
    assertEquals("Single product has expected inchi", expectedDesaltingInchi2,
        mockAPI.getWrittenChemicals().get(newProducts[0]).getInChI());
    assertEquals("Single product has expected coefficient", expectedProductCoefficients[0],
        rxn.getProductCoefficient(newProducts[0]));
  }

  @Test
  public void testChemicalsWithMultiplierThatDesaltToTheSameMoleculeGetSummed() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Map<Long, String> idToInchi = new HashMap<>();

    // The two inchis below, once desalted, map to InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)
    idToInchi.put(1L, "InChI=1S/2C5H5.F6P.Fe/c2*1-2-4-5-3-1;1-7(2,3,4,5)6;/h2*1-5H;;/q3*-1;+3");
    idToInchi.put(2L, "InChI=1S/2C5H5.Fe/c2*1-2-4-5-3-1;/h2*1-5H;/q2*-1;+2");
    idToInchi.put(3L, "InChI=1S/6CN.Fe.3K/c6*1-2;;;;/q;;;;;;-3;3*+1");
    idToInchi.put(4L, "InChI=1S/8CN.W/c8*1-2;/q;;;;;;;;-3");

    String expectedDesaltingInchi1 = "InChI=1S/C5H5/c1-2-4-5-3-1/h1-5H/q-1";
    String expectedDesaltingInchi2 = "InChI=1S/CN/c1-2";

    Long[] substrates1 = {1L, 2L};
    Long[] products = {3L, 4L};

    Integer[] substrateCoefficients = {3, 7};
    Integer[] productCoefficients = {5, 11};

    Integer[] expectedSubstrateCoefficients = {3 * 2 + 7 * 2};
    Integer[] expectedProductCoefficients = {5 * 6 + 11 * 8};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction1);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionDesalter testReactionDesalter = new ReactionDesalter(mockNoSQLAPI);
    testReactionDesalter.init();
    testReactionDesalter.run();

    assertEquals("One desalted reaction should be written", 1,
        mockAPI.getWrittenReactions().size());

    Reaction rxn = mockAPI.getWrittenReactions().get(0);

    Long[] newSubstrates = rxn.getSubstrates();
    assertEquals("One substrate remains after desalting", 1, newSubstrates.length);
    assertEquals("Single substrate has expected inchi", expectedDesaltingInchi1,
        mockAPI.getWrittenChemicals().get(newSubstrates[0]).getInChI());
    assertEquals("Single substrate has expected coefficient", expectedSubstrateCoefficients[0],
        rxn.getSubstrateCoefficient(newSubstrates[0]));

    Long[] newProducts = rxn.getProducts();
    assertEquals("One Product remains after desalting", 1, newProducts.length);
    assertEquals("Single product has expected inchi", expectedDesaltingInchi2,
        mockAPI.getWrittenChemicals().get(newProducts[0]).getInChI());
    assertEquals("Single product has expected coefficient", expectedProductCoefficients[0],
        rxn.getProductCoefficient(newProducts[0]));
  }
}
