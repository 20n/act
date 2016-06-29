package com.act.biointerpretation.mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
import junit.framework.Assert;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MechanisticValidatorTest {

  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ReactionDesalter.class);
    utilsObject = new TestUtils();
  }

  @Test
  public void testAllRosHaveSubstrateAndProductCounts() throws Exception {
    ErosCorpus erosCorpus = new ErosCorpus();
    erosCorpus.loadValidationCorpus();
    for (Ero ro : erosCorpus.getRos()) {
      Assert.assertNotNull(ro.getProduct_count());
      Assert.assertNotNull(ro.getSubstrate_count());
    }
  }

  @Test
  public void testMechanisticValidatorIsMatchingTheCorrectROsToReaction() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    Map<Long, String> idToInchi = new HashMap<>();

    // The first inchi is a cofactor while the second is not.
    idToInchi.put(1L, "InChI=1S/p+1");
    idToInchi.put(2L, "InChI=1S/C5H10O/c1-3-4-5(2)6/h3-4H2,1-2H3");
    idToInchi.put(3L, "InChI=1S/C5H12O/c1-3-4-5(2)6/h5-6H,3-4H2,1-2H3/t5-/m1/s1");

    JSONObject expectedResult = new JSONObject();

    // The first RO is an alcohol oxidation to aldehyde. The score is 4 since the curated ERO list sets the category
    // to perfect.
    expectedResult.put("3", "4");

    // This RO is for primary alcohol to aldehyde. Similar but more specific RO than the above.
    expectedResult.put("337", "4");

    // This RO is for secondary alcohol to aldehyde. Similar again to the above.
    expectedResult.put("340", "4");

    Long[] products = {1L, 2L};
    Long[] substrates = {3L};

    Integer[] substrateCoefficients = {1};
    Integer[] productCoefficients = {1, 1};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    MechanisticValidator mechanisticValidator = new MechanisticValidator(mockNoSQLAPI);
    mechanisticValidator.init();
    mechanisticValidator.run();

    assertEquals("One reaction should be written to the DB", 1, mockAPI.getWrittenReactions().size());
    assertEquals("The mechanistic validator result should be equal to the expected result",
        expectedResult.toString(), mockAPI.getWrittenReactions().get(0).getMechanisticValidatorResult().toString());
  }

  @Test
  public void testMechanisticValidatorDoesNotAddAnyROScoresWhenNoMatchesHappen() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    Map<Long, String> idToInchi = new HashMap<>();

    // The first inchi is a cofactor while the second is not.
    idToInchi.put(1L, "InChI=1S/p+1");
    idToInchi.put(2L, "InChI=1S/H2O/h1H2");

    Long[] products = {2L};
    Long[] substrates = {1L};

    Integer[] substrateCoefficients = {1};
    Integer[] productCoefficients = {1};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    MechanisticValidator mechanisticValidator = new MechanisticValidator(mockNoSQLAPI);
    mechanisticValidator.init();
    mechanisticValidator.run();

    assertEquals("One reaction should be written to the DB", 1, mockAPI.getWrittenReactions().size());
    assertEquals("The mechanistic validator result should be null since no ROs should react with the reaction",
        null, mockAPI.getWrittenReactions().get(0).getMechanisticValidatorResult());
  }

  @Test
  public void testMechanisticValidatorIsMatchingTheCorrectROsToReactionThatAreNotPerfect() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    Map<Long, String> idToInchi = new HashMap<>();

    // The first inchi is a cofactor while the second is not.
    idToInchi.put(1L, "InChI=1S/p+1");
    idToInchi.put(2L, "InChI=1S/C25H40N7O19P3S/c1-25(2,20(38)23(39)28-6-5-14(33)27-7-8-55-16(36)4-3-15(34)35)10-48-54(45,46)51-53(43,44)47-9-13-19(50-52(40,41)42)18(37)24(49-13)32-12-31-17-21(26)29-11-30-22(17)32/h11-13,18-20,24,37-38H,3-10H2,1-2H3,(H,27,33)(H,28,39)(H,34,35)(H,43,44)(H,45,46)(H2,26,29,30)(H2,40,41,42)/t13-,18-,19-,20+,24-/m1/s1");
    idToInchi.put(3L, "InChI=1S/C4H6O3/c5-3-1-2-4(6)7/h3H,1-2H2,(H,6,7)");

    Long[] products = {1L, 2L};
    Long[] substrates = {3L};

    JSONObject expectedResult = new JSONObject();

    // This RO has no name, but it currently in the "validated" category. Hence, the score should be 3.
    expectedResult.put("284", "3");

    Integer[] substrateCoefficients = {1};
    Integer[] productCoefficients = {1, 1};

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    MechanisticValidator mechanisticValidator = new MechanisticValidator(mockNoSQLAPI);
    mechanisticValidator.init();
    mechanisticValidator.run();

    assertEquals("One reaction should be written to the DB", 1, mockAPI.getWrittenReactions().size());
    assertEquals("The mechanistic validator result should be equal to the expected result",
        expectedResult.toString(), mockAPI.getWrittenReactions().get(0).getMechanisticValidatorResult().toString());
  }

  @Test
  public void testMechanisticValidatorIsCorrectlyUsingCoefficients() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    Map<Long, String> idToInchi = new HashMap<>();

    idToInchi.put(1L, "InChI=1S/CH4O/c1-2/h2H,1H3");
    idToInchi.put(2L, "InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)");

    Long[] products = {1L, 2L};
    Long[] substrates = {1L, 2L};

    // These coefficients meet the expectations of the RO we'll look for later.
    Integer[] substrateCoefficients = {2, 1};
    Integer[] productCoefficients = {1, 2};

    JSONObject expectedResult = new JSONObject();

    // This RO acts on two identical substrates.
    expectedResult.put("165", "3");

    Reaction testReaction =
        utilsObject.makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, true);

    testReactions.add(testReaction);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToInchi);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    MechanisticValidator mechanisticValidator = new MechanisticValidator(mockNoSQLAPI);
    mechanisticValidator.init();
    mechanisticValidator.run();

    assertEquals("One reaction should be written to the DB", 1, mockAPI.getWrittenReactions().size());
    assertEquals("The mechanistic validator result should be equal to the expected result",
        expectedResult.toString(), mockAPI.getWrittenReactions().get(0).getMechanisticValidatorResult().toString());
  }
}
