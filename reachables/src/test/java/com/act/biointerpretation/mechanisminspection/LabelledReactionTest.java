package com.act.biointerpretation.mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabelledReactionTest {

  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ReactionDesalter.class);
    utilsObject = new TestUtils();
  }

  @Test
  public void testOnlyExactSetOfSubstratesAndProductsAreRecognizedByLabelledReaction() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] substrates1 = {3L, 4L};
    Long[] products1 = {1L, 2L};
    Integer[] substrateCoefficients1 = {1, 1};
    Integer[] productCoefficients1 = {1, 1};

    // An extra substrate is added in this reaction.
    Long[] substrates2 = {3L, 4L, 5L};
    Long[] products2 = {1L, 2L};
    Integer[] substrateCoefficients2 = {1, 1, 1};
    Integer[] productCoefficients2 = {1, 1};

    Map<Long, String> idToChem = new HashMap<>();
    idToChem.put(1L, "InChI=1S/C6H9NOS/c1-5-6(2-3-8)9-4-7-5/h4,8H,2-3H2,1H3");
    idToChem.put(2L, "InChI=1S/C13H14N4O2/c1-8-15-7-11(12(14)17-8)16-6-9-2-4-10(5-3-9)13(18)19/h2-5,7,16H,6H2,1H3,(H,18,19)(H2,14,15,17)");
    idToChem.put(3L, "InChI=1S/C12H17N4OS/c1-8-11(3-4-17)18-7-16(8)6-10-5-14-9(2)15-12(10)13/h5,7,17H,3-4,6H2,1-2H3,(H2,13,14,15)/q+1");
    idToChem.put(4L, "InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)");
    idToChem.put(5L, "InChI=1S/C11H16NO7P/c12-7-3-1-6(2-4-7)11-10(14)9(13)8(19-11)5-18-20(15,16)17/h1-4,8-11,13-14H,5,12H2,(H2,15,16,17)/t8-,9-,10-,11+/m1/s1");

    Reaction testReaction1 =
        utilsObject.makeTestReaction(substrates1, products1, substrateCoefficients1, productCoefficients1, true,
            "2.5.1.2",
            " {Marsilea drummondii} thiamine + p-aminobenzoic acid -?> 4-methyl-5-(2-hydroxyethyl)-thiazole + 4-[[(4-amino-2-methylpyrimidin-5-yl)methyl]amino]benzoic acid");

    Reaction testReaction2 =
        utilsObject.makeTestReaction(substrates2, products2, substrateCoefficients2, productCoefficients2, true,
            "2.5.1.2",
            " {Marsilea drummondii} thiamine + p-aminobenzoic acid -?> 4-methyl-5-(2-hydroxyethyl)-thiazole + 4-[[(4-amino-2-methylpyrimidin-5-yl)methyl]amino]benzoic acid");

    testReactions.add(testReaction1);
    testReactions.add(testReaction2);

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, utilsObject.SEQUENCES, utilsObject.ORGANISM_NAMES, idToChem);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    LabelledReactionsCorpus reactionsCorpus = new LabelledReactionsCorpus(mockNoSQLAPI);
    reactionsCorpus.loadCorpus();
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(testReaction1));
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(testReaction2));
  }
}
