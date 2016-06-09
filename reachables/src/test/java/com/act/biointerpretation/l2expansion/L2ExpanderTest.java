package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class L2ExpanderTest {

  final String VALID_TEST_METABOLITE = "InChI=1S/C6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2";
  final String INVALID_TEST_METABOLITE = "InChI=1S/C18H21N6O8P/c19-18-23-16-12(17(27)24-18)22-10(6-21-16)" +
          "5-20-9-3-1-8(2-4-9)15-14(26)13(25)11(32-15)7-31-33(28,29)30/h1-4,6,11,13-15,20,25-26H,5,7H2," +
          "(H2,28,29,30)(H3,19,21,23,24,27)";

  final String RO_STRING = "[H][#7:6]([H])-[#6:1]>>[H][#8]-[#6](=[#7:6]-[#6:1])C([H])([H])[H]";

  final String EXPECTED_PRODUCT = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"; //Acetaminophen

  List<String> validMetaboliteCorpus = new ArrayList<>();
  List<String> invalidMetaboliteCorpus = new ArrayList<>();

  List<Ero> validTestRoCorpus = new ArrayList<>();
  List<Ero> invalidTestRoCorpus = new ArrayList<>();

  @Before
  public void setup() throws ReactionException, MolFormatException {
    //Set up valid RO corpus for testing
    Ero validTestEro = new Ero();
    validTestEro.setRo(RO_STRING);
    validTestEro.setSubstrate_count(1);
    validTestRoCorpus.add(validTestEro);

    //Set up multiple-substrate RO corpus for testing
    Ero invalidTestEro = new Ero();
    invalidTestEro.setRo(RO_STRING);
    invalidTestEro.setSubstrate_count(2);
    invalidTestRoCorpus.add(invalidTestEro);

    //Set up metabolite corpus with one metabolite, which should successfully react with RO
    validMetaboliteCorpus.add(VALID_TEST_METABOLITE);

    //Set up metabolite corpus with one metabolite, which should not successfully react with RO
    invalidMetaboliteCorpus.add(INVALID_TEST_METABOLITE);
  }

  @Test
  public void testL2ExpanderPositive() throws Exception {
    // Arrange
    L2Expander expander = new L2Expander(validTestRoCorpus, validMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getSingleSubstratePredictionCorpus();

    // Assert
    assertEquals("Exactly one prediction made,",
            1, predictions.getCorpus().size());
    assertEquals("Correct metabolite predicted",
            VALID_TEST_METABOLITE, predictions.getCorpus().get(0).getSubstrateInchis().get(0));
    assertEquals("Correct RO predicted",
            RO_STRING, predictions.getCorpus().get(0).getRO().getRo());
    assertEquals("Correct product predicted",
            EXPECTED_PRODUCT, predictions.getCorpus().get(0).getProductInchis().get(0));
  }

  @Test
  public void testL2ExpanderNegative_ZeroResults() throws Exception {
    // Arrange
    L2Expander expander = new L2Expander(validTestRoCorpus, invalidMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getSingleSubstratePredictionCorpus();

    // Assert
    assertEquals("No predictions made", 0, predictions.getCorpus().size());
  }

  @Test
  public void testL2ExpanderMultipleSubstrates_ZeroResults() throws Exception {
    // Arrange
    L2Expander expander = new L2Expander(invalidTestRoCorpus, validMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getSingleSubstratePredictionCorpus();

    // Assert
    assertEquals("No predictions made", 0, predictions.getCorpus().size());
  }
}
