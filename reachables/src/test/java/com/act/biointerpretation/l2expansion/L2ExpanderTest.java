package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class L2ExpanderTest {

  List<Ero> testRoCorpus = new ArrayList<>();
  Map<String, Molecule> validMetaboliteCorpus = new HashMap<>();
  Map<String, Molecule> invalidMetaboliteCorpus = new HashMap<>();

  final String VALID_TEST_METABOLITE = "InChI=1S/C6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2";
  final String INVALID_TEST_METABOLITE = "InChI=1S/C18H21N6O8P/c19-18-23-16-12(17(27)24-18)22-10(6-21-16)" +
          "5-20-9-3-1-8(2-4-9)15-14(26)13(25)11(32-15)7-31-33(28,29)30/h1-4,6,11,13-15,20,25-26H,5,7H2," +
          "(H2,28,29,30)(H3,19,21,23,24,27)";
  final String RO_STRING = "[H][#7:6]([H])-[#6:1]>>[H][#8]-[#6](=[#7:6]-[#6:1])C([H])([H])[H]";
  final String EXPECTED_PRODUCT = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"; //Acetaminophen

  L2MetaboliteCorpus mockMetaboliteCorpus = mock(L2MetaboliteCorpus.class);

  @Before
  public void setup() throws ReactionException, MolFormatException {
    //Set up RO corpus for testing
    Ero testEro = new Ero();
    testEro.setRo(RO_STRING);
    testRoCorpus.add(testEro);

    //Set up metabolite corpus with one metabolite, which should successfully react with RO
    Molecule validTestMolecule = MolImporter.importMol(VALID_TEST_METABOLITE);
    validMetaboliteCorpus.put(VALID_TEST_METABOLITE, validTestMolecule);

    //Set up metabolite corpus with one metabolite, which should not successfully react with RO
    Molecule invalidTestMolecule = MolImporter.importMol(INVALID_TEST_METABOLITE);
    validMetaboliteCorpus.put(INVALID_TEST_METABOLITE, invalidTestMolecule);
  }

  @Test
  public void testL2ExpanderPositive_OneResult() throws Exception {
    // Arrange
    when(mockMetaboliteCorpus.getCorpus()).thenReturn(validMetaboliteCorpus);
    L2Expander expander = new L2Expander(testRoCorpus, mockMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getPredictionCorpus();

    // Assert
    assertEquals(predictions.getCorpus().size(), 1);
  }

  @Test
  public void testL2ExpanderPositive_SubstrateMatches() throws Exception {
    // Arrange
    when(mockMetaboliteCorpus.getCorpus()).thenReturn(validMetaboliteCorpus);
    L2Expander expander = new L2Expander(testRoCorpus, mockMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getPredictionCorpus();

    // Assert
    assertEquals(predictions.getCorpus().get(0).getSubstrateInchis()[0], VALID_TEST_METABOLITE);
  }

  @Test
  public void testL2ExpanderPositive_RoMatches() throws Exception {
    // Arrange
    when(mockMetaboliteCorpus.getCorpus()).thenReturn(validMetaboliteCorpus);
    L2Expander expander = new L2Expander(testRoCorpus, mockMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getPredictionCorpus();

    // Assert
    assertEquals(predictions.getCorpus().get(0).getRO().getRo(), RO_STRING);
  }

  @Test
  public void testL2ExpanderPositive_ProductMatches() throws Exception {
    // Arrange
    when(mockMetaboliteCorpus.getCorpus()).thenReturn(validMetaboliteCorpus);
    L2Expander expander = new L2Expander(testRoCorpus, mockMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getPredictionCorpus();

    // Assert
    assertEquals(predictions.getCorpus().get(0).getProductInchis()[0], EXPECTED_PRODUCT);
  }

  @Test
  public void testL2ExpanderNegative_ZeroResults() throws Exception {
    // Arrange
    when(mockMetaboliteCorpus.getCorpus()).thenReturn(invalidMetaboliteCorpus);
    L2Expander expander = new L2Expander(testRoCorpus, mockMetaboliteCorpus);

    // Execute
    L2PredictionCorpus predictions = expander.getPredictionCorpus();

    // Assert
    assertEquals(predictions.getCorpus().size(), 0);
  }
}
