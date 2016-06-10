package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReactionsFilterTest {

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_PRODUCED_ID = new Long(2);
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);

  final List<String> SUBSTRATE_INCHIS = Arrays.asList("substrate_inchi");

  final Integer ERO_ID = new Integer(5);

  final List<String> PRODUCT_INCHIS = Arrays.asList("substrate_inchi");

  final Long REACTION_ID = new Long(6);

  Reaction reaction = new Reaction(REACTION_ID,
          new Long[]{SUBSTRATE_ID},
          new Long[]{PRODUCT_PRODUCED_ID},
          new Long[]{}, new Long[]{}, new Long[]{},
          "", ConversionDirectionType.LEFT_TO_RIGHT,
          StepDirection.LEFT_TO_RIGHT,
          "", Reaction.RxnDetailType.ABSTRACT
          );

  Ero ero = new Ero();

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  JSONObject validationRoMatch = Mockito.mock(JSONObject.class);
  JSONObject validationNoRoMatch = Mockito.mock(JSONObject.class);

  @Before
  public void setup() {
    // Set up ERO ID on ERO and MechanisticValidatorResult
    ero.setId(ERO_ID);
    Mockito.when(validationRoMatch.keys()).thenReturn(Arrays.asList(String.valueOf(ERO_ID)).iterator());
    Mockito.when(validationNoRoMatch.keys()).thenReturn(Arrays.asList().iterator());

    // Set up reactions DB.
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_PRODUCED_ID))).
            thenReturn(Arrays.asList(reaction));
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_NOT_PRODUCED_ID))).
            thenReturn(new ArrayList<Reaction>());
  }

  @Test
  public void testReactionInDBRoMatch() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_PRODUCED_ID);
    reaction.setMechanisticValidatorResult(validationRoMatch);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Reaction in DB- should return one result.", 1, result.size());
    assertEquals("Should return one matching reaction.", 1, result.get(0).getReactionsRoMatch().size());
    assertEquals("Reaction ID should match DB response.", REACTION_ID,
            result.get(0).getReactionsRoMatch().get(0));
    assertTrue("Should return no non-matching reactions.", result.get(0).getReactionsNoRoMatch().isEmpty());
  }

  @Test
  public void testReactionInDBNoRoMatch() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_PRODUCED_ID);
    reaction.setMechanisticValidatorResult(validationNoRoMatch);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Reaction in DB- should return one result.", 1, result.size());
    assertEquals("Should return one non-matching reaction.", 1, result.get(0).getReactionsNoRoMatch().size());
    assertEquals("Reaction ID should match DB response.", REACTION_ID,
            result.get(0).getReactionsNoRoMatch().get(0));
    assertTrue("Should return no matching reactions.", result.get(0).getReactionsRoMatch().isEmpty());
  }

  @Test
  public void testReactionNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_NOT_PRODUCED_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Reaction not in DB- should still return one result.", 1, result.size());
    assertTrue("Should return no matching reaction.", result.get(0).getReactionsRoMatch().isEmpty());
    assertTrue("Should return no non-matching reaction.", result.get(0).getReactionsNoRoMatch().isEmpty());
  }

  @Test
  public void testReactionSubstrateEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_INCHIS);
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
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("No product- should return empty list", result.isEmpty());
  }
}
