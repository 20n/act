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

    Integer[] substrateCoefficients = {2};
    Integer[] productCoefficients = {3};

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

    CofactorRemover cofactorRemover = new CofactorRemover(mockNoSQLAPI);
    cofactorRemover.run();

    assertEquals("Similar pre-cofactor removal substrates should be written as two entries", 2,
        mockAPI.getWrittenReactions().size());

    assertEquals("Since the first reaction had a cofactor in the substrates, there should no substrates after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrates().length, 0);

    assertEquals("Since the first reaction had a cofactor in the substrates, there should no substrate coefficients after the cofactor" +
        " is removed", mockAPI.getWrittenReactions().get(0).getSubstrateIdsOfSubstrateCoefficients().size(), 0);

    assertEquals("There should be one entry in the substrate cofactors list",
        mockAPI.getWrittenReactions().get(0).getSubstrateCofactors().length, 1);

    assertEquals("The substrate cofactor should match the correct substrate id",
        mockAPI.getWrittenReactions().get(0).getSubstrateCofactors()[0].longValue(), 1L);

    assertEquals("Since the second reaction does not have a cofactor in the substrates, there should a substrate in the " +
        "write db", mockAPI.getWrittenReactions().get(1).getSubstrates().length, 1);

    // The id is 3 since it is the third chemical written in the write db.
    assertEquals("The write substrate should match the correct read substrate id",
        mockAPI.getWrittenReactions().get(1).getSubstrates()[0].longValue(), 3L);

    assertEquals("There should be no entry in the substrate cofactors list",
        mockAPI.getWrittenReactions().get(1).getSubstrateCofactors().length, 0);

    assertEquals("The substrate coefficient should be preserved across the transformation",
        mockAPI.getWrittenReactions().get(1).getSubstrateCoefficient(3L), new Integer(2));
  }
}
