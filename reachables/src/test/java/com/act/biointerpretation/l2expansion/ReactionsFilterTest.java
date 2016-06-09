package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReactionsFilterTest {

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  final String SUBSTRATE_INCHI = "substrate_in_db";
  final Long VALID_ID_SUBSTRATE = new Long(1);

  final String PRODUCT_PRODUCED_INCHI = "product_produced";
  final Long PRODUCT_PRODUCED_ID = new Long(2);

  final String PRODUCT_NOT_PRODUCED_INCHI = "product_not_produced";
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);

  final String INVALID_INCHI = "not_in_db";

  final Chemical SUBSTRATE_CHEMICAL = new Chemical(VALID_ID_SUBSTRATE);
  final Chemical PRODUCT_PRODUCED_CHEMICAL = new Chemical(PRODUCT_PRODUCED_ID);
  final Chemical PRODUCT_NOT_PRODUCED_CHEMICAL = new Chemical(PRODUCT_NOT_PRODUCED_ID);

  final Long RXN_ID = new Long(1);

  final Ero DUMMY_ERO = new Ero();

  @Before
  public void setup() throws ReactionException, MolFormatException {
    SUBSTRATE_CHEMICAL.setInchi(SUBSTRATE_INCHI);
    PRODUCT_PRODUCED_CHEMICAL.setInchi(PRODUCT_PRODUCED_INCHI);
    PRODUCT_NOT_PRODUCED_CHEMICAL.setInchi(PRODUCT_NOT_PRODUCED_INCHI);

    //Set up mock mongo db
    Mockito.when(mockMongo.getChemicalFromInChI(SUBSTRATE_INCHI)).thenReturn(SUBSTRATE_CHEMICAL);
    Mockito.when(mockMongo.getChemicalFromInChI(PRODUCT_PRODUCED_INCHI)).thenReturn(PRODUCT_PRODUCED_CHEMICAL);
    Mockito.when(mockMongo.getChemicalFromInChI(PRODUCT_NOT_PRODUCED_INCHI)).thenReturn(PRODUCT_NOT_PRODUCED_CHEMICAL);
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);

    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(VALID_ID_SUBSTRATE), Arrays.asList(PRODUCT_PRODUCED_ID))).
            thenReturn(Arrays.asList(RXN_ID));
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(VALID_ID_SUBSTRATE), Arrays.asList(PRODUCT_NOT_PRODUCED_ID))).
            thenReturn(new ArrayList<Long>());
  }

  @Test
  public void testReactionInDB() throws Exception {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI));

    Predicate<L2Prediction> filter = new ReactionsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertTrue("Reaction in DB- should pass", result);
  }

  @Test
  public void testReactionNotInDB() throws Exception {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_NOT_PRODUCED_INCHI));

    Predicate<L2Prediction> filter = new ReactionsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("Reaction not in DB- should fail", result);
  }

  @Test
  public void testReactionSubstrateNotInDB() throws Exception {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI, INVALID_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI));

    Predicate<L2Prediction> filter = new ReactionsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("One substrate note in DB- should fail", result);
  }

  @Test
  public void testReactionProductNotInDB() throws Exception {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI, INVALID_INCHI));

    Predicate<L2Prediction> filter = new ReactionsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("One product not in DB- should fail", result);
  }
}
