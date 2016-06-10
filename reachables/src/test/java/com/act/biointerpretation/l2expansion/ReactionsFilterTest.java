package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReactionsFilterTest {

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_PRODUCED_ID = new Long(2);
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);

  final Long RXN_ID = new Long(4);

  final List<String> SUBSTRATE_INCHIS = Arrays.asList("substrate_inchi");
  final Ero DUMMY_ERO = new Ero();
  final List<String> PRODUCT_INCHIS = Arrays.asList("substrate_inchi");

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  @Before
  public void setup() {
    // Set up reactions DB.
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_PRODUCED_ID))).
            thenReturn(Arrays.asList(RXN_ID));
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_NOT_PRODUCED_ID))).
            thenReturn(new ArrayList<Long>());
  }

  @Test
  public void testReactionInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, DUMMY_ERO, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_PRODUCED_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Reaction in DB- should return one result.", 1, result.size());
    assertEquals("Should return one matching reaction.", 1, result.get(0).getReactions().size());
    assertEquals("Reaction ID should match DB response.", RXN_ID,
            result.get(0).getReactions().get(0));
  }

  @Test
  public void testReactionNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, DUMMY_ERO, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_NOT_PRODUCED_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Reaction not in DB- should still return one result.", 1, result.size());
    assertTrue("Should return no matching reaction.", result.get(0).getReactions().isEmpty());
  }

  @Test
  public void testReactionSubstrateEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, DUMMY_ERO, PRODUCT_INCHIS);
    testPrediction.addProductId(PRODUCT_PRODUCED_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("No substrate- should return empty list", result.isEmpty());
  }

  @Test
  public void testReactionProductEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, DUMMY_ERO, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("No product- should return empty list", result.isEmpty());
  }
}
