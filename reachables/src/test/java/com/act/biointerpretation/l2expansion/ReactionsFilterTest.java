package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
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

  final String SUBSTRATE_INCHI = "substrate_in_db";
  final Long SUBSTRATE_ID = new Long(1);

  final String PRODUCT_PRODUCED_INCHI = "product_produced";
  final Long PRODUCT_PRODUCED_ID = new Long(2);

  final String PRODUCT_NOT_PRODUCED_INCHI = "product_not_produced";
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);

  final String INVALID_INCHI = "not_in_db";

  final Chemical SUBSTRATE_CHEMICAL = new Chemical(SUBSTRATE_ID);
  final Chemical PRODUCT_PRODUCED_CHEMICAL = new Chemical(PRODUCT_PRODUCED_ID);
  final Chemical PRODUCT_NOT_PRODUCED_CHEMICAL = new Chemical(PRODUCT_NOT_PRODUCED_ID);

  final Long RXN_ID = new Long(4);

  final Ero DUMMY_ERO = new Ero();

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  @Before
  public void setup() {
    // Set chemical inchis
    SUBSTRATE_CHEMICAL.setInchi(SUBSTRATE_INCHI);
    PRODUCT_PRODUCED_CHEMICAL.setInchi(PRODUCT_PRODUCED_INCHI);
    PRODUCT_NOT_PRODUCED_CHEMICAL.setInchi(PRODUCT_NOT_PRODUCED_INCHI);

    // Set up mock mongo db
    Mockito.when(mockMongo.getIdsFromInChIs(Arrays.asList(SUBSTRATE_INCHI))).
            thenReturn(Arrays.asList(SUBSTRATE_ID));
    Mockito.when(mockMongo.getIdsFromInChIs(Arrays.asList(PRODUCT_PRODUCED_INCHI))).
            thenReturn(Arrays.asList(PRODUCT_PRODUCED_ID));
    Mockito.when(mockMongo.getIdsFromInChIs(Arrays.asList(PRODUCT_NOT_PRODUCED_INCHI))).
            thenReturn(Arrays.asList(PRODUCT_NOT_PRODUCED_ID));
    Mockito.when(mockMongo.getIdsFromInChIs(Arrays.asList(SUBSTRATE_INCHI, INVALID_INCHI))).
            thenReturn(Arrays.asList(SUBSTRATE_ID));
    Mockito.when(mockMongo.getIdsFromInChIs(Arrays.asList(PRODUCT_PRODUCED_INCHI, INVALID_INCHI))).
            thenReturn(Arrays.asList(PRODUCT_PRODUCED_ID));

    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_PRODUCED_ID))).
            thenReturn(Arrays.asList(RXN_ID));
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_NOT_PRODUCED_ID))).
            thenReturn(new ArrayList<Long>());
  }

  @Test
  public void testReactionInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI));

    PredictionFilter filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.test(testPrediction);

    // Assert
    assertTrue("Reaction in DB- should pass", result);
  }

  @Test
  public void testReactionNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_NOT_PRODUCED_INCHI));

    PredictionFilter filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.test(testPrediction);

    // Assert
    assertFalse("Reaction not in DB- should fail", result);
  }

  @Test
  public void testReactionSubstrateNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI, INVALID_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI));

    PredictionFilter filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.test(testPrediction);

    // Assert
    assertFalse("One substrate note in DB- should fail", result);
  }

  @Test
  public void testReactionProductNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(
            Arrays.asList(SUBSTRATE_INCHI), DUMMY_ERO, Arrays.asList(PRODUCT_PRODUCED_INCHI, INVALID_INCHI));

    PredictionFilter filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.test(testPrediction);

    // Assert
    assertFalse("One product not in DB- should fail", result);
  }
}
