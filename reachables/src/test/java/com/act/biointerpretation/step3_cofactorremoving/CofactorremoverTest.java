package com.act.biointerpretation.step3_cofactorremoving;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
import com.act.biointerpretation.step3_cofactorremoval.CofactorRemover;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as two entries", 2,
        mockAPI.getWrittenReactions().size());

    assertEquals("Since the first reaction had a cofactor in the substrates, there should one substrate after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrates().length, 1);

    assertEquals("Since the first reaction had a cofactor in the substrates, there should be one substrate coefficients after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrateIdsOfSubstrateCoefficients().size(), 1);

    assertEquals("There should be one entry in the substrate cofactors list",
        mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length, 1);

    assertEquals("The substrate cofactor should match the correct substrate id",
        mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue(), 1L);

    assertEquals("The write substrate should match the correct read substrate id",
        mockAPI.getWrittenChemicals().get(
            mockAPI.getWrittenReactions().get(1).getSubstrates()[0].longValue()).getInChI(), "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)/p-1");

    assertEquals("Since the second reaction does not have a cofactor in the substrates, there should a substrate in the " +
        "write db", mockAPI.getWrittenReactions().get(1).getSubstrates().length, 1);

    assertEquals("There should be no entry in the substrate cofactors list",
        mockAPI.getWrittenReactions().get(1).getSubstrateCofactors().length, 0);
  }

  @Test
  public void testHighRankingCofactorShouldBeRemovedAndTheLowRankingCofactorShouldStay() throws IOException {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] products = {4L};

    Map<Long, String> idToInchi = new HashMap<>();

    // This is a rank 3 cofactor (should not be removed if it is the only substrate)
    String testInchi1 = "InChI=1/C10H15N4O15P3/c15-5-3(1-26-31(22,23)29-32(24,25)28-30(19,20)21)27-9(6(5)16)14-2-11-4-7(14)12-10(18)13-8(4)17/h2-3,5-6,9,15-16H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H2,12,13,17,18)";
    idToInchi.put(1L, testInchi1);

    // This is a rank 2 cofactor (should be removed)
    String test2 = "InChI=1S/C14H20O4/c1-8(2)6-7-10-9(3)11(15)13(17-4)14(18-5)12(10)16/h6,15-16H,7H2,1-5H3";
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
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as one entry", 1,
        mockAPI.getWrittenReactions().size());

    assertEquals("The first reaction had 3 cofactors in the substrates, but there should be only one substrate after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrates().length, 1);

    assertEquals("The substrate name should be the same as we expect",
        mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrates()[0].longValue()).getInChI(), testInchi1);

    assertEquals("Since the first reaction had a cofactor in the substrates, there should be one substrate coefficients after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrateIdsOfSubstrateCoefficients().size(), 1);

    assertEquals("There should be two entries in the substrate cofactors list",
        mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length, 2);

    assertEquals("The substrate cofactor should match the correct substrate id",
        mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue()).getInChI(), test2);

    assertEquals("The substrate cofactor should match the correct substrate id",
        mockAPI.getWrittenChemicals().get(mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[1].longValue()).getInChI(), test3);
  }
}
