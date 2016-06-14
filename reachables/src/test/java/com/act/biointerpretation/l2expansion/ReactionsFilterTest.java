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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReactionsFilterTest {

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_PRODUCED_ID = new Long(2);
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);

  String SUBSTRATE_INCHI = "substrate_inchi";
  final List<String> SUBSTRATE_INCHIS = Arrays.asList(SUBSTRATE_INCHI);

  final Integer ERO_ID = new Integer(5);

  String PRODUCT_PRODUCED_INCHI = "product_produced_inchi";
  String PRODUCT_NOT_PRODUCED_INCHI = "product_not_produced_inchi";
  final List<String> PRODUCT_PRODUCED_INCHIS = Arrays.asList(PRODUCT_PRODUCED_INCHI);
  final List<String> PRODUCT_NOT_PRODUCED_INCHIS = Arrays.asList(PRODUCT_NOT_PRODUCED_INCHI);

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

  MongoDB mockMongo;

  JSONObject validationRoMatch;
  JSONObject validationNoRoMatch;

  @Before
  public void setup() {
    // Set up ERO ID on ERO
    ero.setId(ERO_ID);

    // Set up mechanistic validation results
    validationRoMatch = Mockito.mock(JSONObject.class);
    validationNoRoMatch = Mockito.mock(JSONObject.class);
    Set<String> validationSet = new HashSet<>();
    validationSet.add(ERO_ID.toString());
    Mockito.when(validationRoMatch.keySet()).thenReturn(validationSet);
    Mockito.when(validationNoRoMatch.keySet()).thenReturn(new HashSet<Integer>());

    // Set up reactions DB.
    mockMongo = Mockito.mock(MongoDB.class);
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_PRODUCED_ID))).
            thenReturn(Arrays.asList(reaction));
    Mockito.when(mockMongo.getRxnsWithAll(Arrays.asList(SUBSTRATE_ID), Arrays.asList(PRODUCT_NOT_PRODUCED_ID))).
            thenReturn(new ArrayList<Reaction>());
  }

  @Test
  public void testReactionInDBRoMatch() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_PRODUCED_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_INCHI, SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_PRODUCED_INCHI, PRODUCT_PRODUCED_ID);
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
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_PRODUCED_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_INCHI, SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_PRODUCED_INCHI, PRODUCT_PRODUCED_ID);
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
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_NOT_PRODUCED_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_INCHI, SUBSTRATE_ID);
    testPrediction.addProductId(PRODUCT_NOT_PRODUCED_INCHI, PRODUCT_NOT_PRODUCED_ID);

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
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_PRODUCED_INCHIS);
    testPrediction.addProductId(PRODUCT_PRODUCED_INCHI, PRODUCT_PRODUCED_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("No substrate- should return empty list", result.isEmpty());
  }

  @Test
  public void testReactionProductEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(SUBSTRATE_INCHIS, ero, PRODUCT_PRODUCED_INCHIS);
    testPrediction.addSubstrateId(SUBSTRATE_INCHI, SUBSTRATE_ID);

    Function<L2Prediction, List<L2Prediction>> filter = new ReactionsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("No product- should return empty list", result.isEmpty());
  }
}
