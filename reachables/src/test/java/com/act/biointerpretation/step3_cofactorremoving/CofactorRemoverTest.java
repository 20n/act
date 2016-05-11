package com.act.biointerpretation.step3_cofactorremoving;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
import com.act.biointerpretation.step3_cofactorremoval.CofactorRemover;
import com.act.biointerpretation.step3_cofactorremoval.CofactorsCorpus;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CofactorRemoverTest {

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
  public void testCofactorCorpusHasCorrectInchies() throws Exception {
    CofactorsCorpus cofactorsCorpus = new CofactorsCorpus();
    cofactorsCorpus.loadCorpus();
    for (Map.Entry<String, String> inchiToName : cofactorsCorpus.getInchiToName().entrySet()) {
      try {
        MolImporter.importMol(inchiToName.getKey());
      } catch (Exception e) {
        Assert.fail(String.format("Error importing molecule from the inchi: %s with error message %s",
            inchiToName.getKey(), e.getMessage()));
      }
    }
  }

  @Test
  public void testReactionWithCofactorIsTransformedCorrectlyWhileReactionWithoutCofactorIsNottransformed() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // The first inchi is a cofactor while the second is not.
    idToInchi.put(1L, "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)");
    idToInchi.put(2L, "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");
    idToInchi.put(3L, "InChI=1S/C7H5Cl3/c8-4-5-2-1-3-6(9)7(5)10/h1-3H,4H2");

    Long[] substrates1 = {1L, 3L};
    Long[] substrates2 = {2L};

    Integer[] substrateCoefficients1 = {2, 3};
    Integer[] substrateCoefficients2 = {2};
    Integer[] productCoefficients = {3};

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products, substrateCoefficients1, productCoefficients, true);

    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates2, products, substrateCoefficients2, productCoefficients, true);

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.init();
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as two entries", 2,
        mockAPI.getWrittenReactions().size());

    assertEquals("Since the first reaction had a cofactor in the substrates, there should one substrate after the cofactor" +
        " is removed", 1, mockAPI.getWrittenReactions().get(0).getSubstrates().length);

    assertEquals("Since the first reaction had a cofactor in the substrates, there should be one substrate coefficients after the cofactor" +
        " is removed", 1, mockAPI.getWrittenReactions().get(0).getSubstrateIdsOfSubstrateCoefficients().size());

    assertEquals("There should be one entry in the substrate cofactors list",
        1, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length);

    assertEquals("The substrate cofactor should match the correct substrate id",
        1L, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue());

    assertEquals("The write substrate should match the correct read substrate id",
        "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1",
        mockAPI.getWrittenChemicals().get(
            mockAPI.getWrittenReactions().get(1).getSubstrates()[0].longValue()).getInChI());

    assertEquals("Since the second reaction does not have a cofactor in the substrates, there should a substrate in the " +
        "write db", 1, mockAPI.getWrittenReactions().get(1).getSubstrates().length);

    assertEquals("There should be no entry in the substrate cofactors list",
        0, mockAPI.getWrittenReactions().get(1).getSubstrateCofactors().length);
  }

  @Test
  public void testAllCofactorsShouldBeRemoved() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // This is a rank 3 cofactor (should be removed)
    String testInchi1 = "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)";
    idToInchi.put(1L, testInchi1);

    // This is a rank 1 cofactor (should be removed)
    String test2 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(2L, test2);

    // This is a rank 1 cofactor (should be removed)
    String test3 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(3L, test3);

    Integer[] substrateCoefficients = {2, 3, 3};
    Integer[] productCoefficients = {3};

    Long[] substrates = {1L, 2L, 3L};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.init();
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as one entry", 1,
        mockAPI.getWrittenReactions().size());

    assertEquals("The first reaction had 3 cofactors in the substrates, but there should be no substrates after the cofactor" +
        " is removed", 0, mockAPI.getWrittenReactions().get(0).getSubstrates().length);

    assertEquals("There should be three entries in the substrate cofactors list",
        3, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length);

    assertEquals("The substrate cofactor should match the correct substrate id",
        testInchi1, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue()).getInChI());

    assertEquals("The substrate cofactor should match the correct substrate id",
        test2, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[1].longValue()).getInChI());

    assertEquals("The substrate cofactor should match the correct substrate id",
        test3, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[2].longValue()).getInChI());
  }

  @Test
  public void testExistingCofactorsAreNotOverwritten() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    String testInchi1 = "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)";
    idToInchi.put(1L, testInchi1);

    String test2 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(2L, test2);

    String test3 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(3L, test3);

    Integer[] substrateCoefficients = {2, 3};
    Integer[] productCoefficients = {3};

    Long[] substrates = {1L, 2L};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);
    // Set the third chemical as an existing cofactor to ensure it is not overwritten.
    testReaction.setSubstrateCofactors(new Long[]{3L});

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, Arrays.asList(3L), utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.init();
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as one entry", 1,
        mockAPI.getWrittenReactions().size());

    assertEquals("The first reaction had 2 cofactors in the substrates, but there should be no substrates after the cofactor" +
        " is removed", 0, mockAPI.getWrittenReactions().get(0).getSubstrates().length);

    assertEquals("There should be three entries in the substrate cofactors list",
        3, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length);

    assertEquals("The substrate cofactor that was already in the reaction should persist and appear first",
        test3, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue()).getInChI());

    assertEquals("The substrate cofactor should match the correct substrate id",
        testInchi1, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[1].longValue()).getInChI());

    assertEquals("The substrate cofactor should match the correct substrate id",
        test2, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[2].longValue()).getInChI());
  }

  @Test
  public void testReactionWithNoEffectIsNotWritten() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Map<Long, String> idToInchi = new HashMap<>();

    String testInchi1 = "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)";
    idToInchi.put(1L, testInchi1);

    Integer[] substrateCoefficients = {3};
    Integer[] productCoefficients = {3};

    Long[] substrates = {1L};
    Long[] products = {1L};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);
    // Set the third chemical as an existing cofactor to ensure it is not overwritten.
    testReaction.setSubstrateCofactors(new Long[]{3L});

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.init();
    cofactorRemover.run();

    assertEquals("A reaction that imparts no change to its substrate should not be written", 0,
        mockAPI.getWrittenReactions().size());
  }

  @Test
  public void testReactionThatChangesOnlyCofactorsIsStillWritten() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Map<Long, String> idToInchi = new HashMap<>();

    String testInchi1 = "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)";
    idToInchi.put(1L, testInchi1);

    String test2 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(2L, test2);

    String test3 = "InChI=1/C14H20N6O5S/c15-6(14(23)24)1-2-26-3-7-9(21)10(22)13(25-7)20-5-19-8-11(16)17-4-18-12(8)20/h4-7,9-10,13,21-22H,1-3,15H2,(H,23,24)(H2,16,17,18)";
    idToInchi.put(3L, test3);

    Integer[] substrateCoefficients = {1};
    Integer[] productCoefficients = {1};

    Long[] substrates = {1L};
    Long[] products = {1L};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);
    // Set the third chemical as an existing cofactor to ensure it is not overwritten.
    testReaction.setSubstrateCofactors(new Long[]{2L});
    testReaction.setProductCofactors(new Long[]{3L});

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, Arrays.asList(2L, 3L), utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.init();
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as one entry", 1,
        mockAPI.getWrittenReactions().size());

    assertEquals("The first reaction had 1 coenzyme in the substrates, " +
        "but there should be no substrates after the cofactor is removed",
        0, mockAPI.getWrittenReactions().get(0).getSubstrates().length);

    assertEquals("The first reaction had 1 coenzyme in the products, " +
            "but there should be no products after the cofactor is removed",
        0, mockAPI.getWrittenReactions().get(0).getProducts().length);

    assertEquals("The old substrate/product should now exist as a coenzyme",
        testInchi1, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getCoenzymes()[0].longValue()).getInChI());

    assertEquals("The original substrate cofactor should remain",
        1, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length);

    assertEquals("The substrate cofactor that was already in the reaction should persist",
        test2, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue()).getInChI());

    assertEquals("The original product cofactor should remain",
        1, mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length);

    assertEquals("The product cofactor should match the correct substrate id",
        test3, mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getProductCofactors()[0].longValue()).getInChI());
  }
}
