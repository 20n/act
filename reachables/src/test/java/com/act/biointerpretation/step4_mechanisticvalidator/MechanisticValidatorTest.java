package com.act.biointerpretation.step4_mechanisticvalidator;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
import com.act.biointerpretation.step4_mechanisminspection.MechanisticValidator;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
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
    mechanisticValidator.loadCorpus();
    mechanisticValidator.initReactors();
    mechanisticValidator.run();

    assertEquals("One reaction should be written to the DB", 1, mockAPI.getWrittenReactions().size());
    assertEquals("The mechanistic validator result should be equal to the expected result",
        expectedResult.toString(), mockAPI.getWrittenReactions().get(0).getMechanisticValidatorResult().toString());
  }

}
