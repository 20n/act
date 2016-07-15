package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.sars.Sar;
import chemaxon.reaction.Reactor;
import com.act.biointerpretation.sars.SerializableReactor;
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

public class ReactionsTransformerTest {

  final String SUBSTRATE_INCHI = "substrate_inchi";
  final Long SUBSTRATE_ID = new Long(1);
  final String SUBSTRATE_NAME = "substrate_name";
  final L2PredictionChemical SUBSTRATE_PREDICTION_CHEMICAL = new L2PredictionChemical(
      SUBSTRATE_INCHI,
      SUBSTRATE_ID,
      SUBSTRATE_NAME);

  final String PRODUCT_PRODUCED_INCHI = "product_produced_inchi";
  final Long PRODUCT_PRODUCED_ID = new Long(2);
  final String PRODUCT_PRODUCED_NAME = "product_produced_name";
  final L2PredictionChemical PRODUCT_PRODUCED_CHEMICAL = new L2PredictionChemical(
      PRODUCT_PRODUCED_INCHI,
      PRODUCT_PRODUCED_ID,
      PRODUCT_PRODUCED_NAME);

  final String PRODUCT_NOT_PRODUCED_INCHI = "product_not_produced_inchi";
  final Long PRODUCT_NOT_PRODUCED_ID = new Long(3);
  final String PRODUCT_NOT_PRODUCED_NAME = "product_not_produced_name";
  final L2PredictionChemical PRODUCT_NOT_PRODUCED_CHEMICAL = new L2PredictionChemical(
      PRODUCT_NOT_PRODUCED_INCHI,
      PRODUCT_NOT_PRODUCED_ID,
      PRODUCT_NOT_PRODUCED_NAME);

  final String ONLY_INCHI = "only_an_inchi";
  final L2PredictionChemical ONLY_INCHI_CHEMICAL = new L2PredictionChemical(ONLY_INCHI);

  final Reactor DUMMY_REACTOR = new Reactor();
  final Integer ERO_ID = new Integer(5);
  final Integer SEQ_ID = new Integer(6);
  final SerializableReactor REACTOR = new SerializableReactor(DUMMY_REACTOR, ERO_ID, SEQ_ID);

  final Integer PREDICTION_ID = new Integer(6);

  final List<Sar> NO_SAR = new ArrayList<>();

  final Long REACTION_ID = new Long(6);
  Reaction reaction = new Reaction(REACTION_ID,
      new Long[] {SUBSTRATE_ID},
      new Long[] {PRODUCT_PRODUCED_ID},
      new Long[] {}, new Long[] {}, new Long[] {},
      "", ConversionDirectionType.LEFT_TO_RIGHT,
      StepDirection.LEFT_TO_RIGHT,
      "", Reaction.RxnDetailType.ABSTRACT
  );

  MongoDB mockMongo;

  JSONObject validationRoMatch;
  JSONObject validationNoRoMatch;

  @Before
  public void setup() {
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
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(PRODUCT_PRODUCED_CHEMICAL));
    testPrediction.getSubstrates().get(0).setId(SUBSTRATE_ID);
    testPrediction.getProducts().get(0).setId(PRODUCT_PRODUCED_ID);
    reaction.setMechanisticValidatorResult(validationRoMatch);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("Should return one matching reaction.", 1, result.getReactionsRoMatch().size());
    assertEquals("Reaction ID should match DB response.", REACTION_ID,
        result.getReactionsRoMatch().get(0));
    assertTrue("Should return no non-matching reactions.", result.getReactionsNoRoMatch().isEmpty());
  }

  @Test
  public void testReactionInDBNoRoMatch() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(PRODUCT_PRODUCED_CHEMICAL));
    reaction.setMechanisticValidatorResult(validationNoRoMatch);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("Should return one non-matching reaction.", 1, result.getReactionsNoRoMatch().size());
    assertEquals("Reaction ID should match DB response.", REACTION_ID,
        result.getReactionsNoRoMatch().get(0));
    assertTrue("Should return no matching reactions.", result.getReactionsRoMatch().isEmpty());
  }

  @Test
  public void testReactionNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(PRODUCT_NOT_PRODUCED_CHEMICAL));
    testPrediction.getSubstrates().get(0).setId(SUBSTRATE_ID);
    testPrediction.getProducts().get(0).setId(PRODUCT_NOT_PRODUCED_ID);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertTrue("Should return no matching reaction.", result.getReactionsRoMatch().isEmpty());
    assertTrue("Should return no non-matching reaction.", result.getReactionsNoRoMatch().isEmpty());
  }

  @Test
  public void testReactionSubstrateEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(ONLY_INCHI_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(PRODUCT_PRODUCED_CHEMICAL));
    testPrediction.getProducts().get(0).setId(PRODUCT_PRODUCED_ID);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("No substrate- should return no reactions", 0, result.getReactionCount());
  }

  @Test
  public void testReactionProductEmpty() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(ONLY_INCHI_CHEMICAL));

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("No product- should return no reactions", 0, result.getReactionCount());
  }

  @Test
  public void testReactionOneSubstrateNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(ONLY_INCHI_CHEMICAL, SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(PRODUCT_PRODUCED_CHEMICAL));
    testPrediction.getProducts().get(0).setId(PRODUCT_PRODUCED_ID);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("One substrate has no ID- should return no reactions", 0, result.getReactionCount());
  }


  @Test
  public void testReactionOneProductNotInDB() {
    // Arrange
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID,
        Arrays.asList(SUBSTRATE_PREDICTION_CHEMICAL),
        REACTOR,
        NO_SAR,
        Arrays.asList(ONLY_INCHI_CHEMICAL, PRODUCT_PRODUCED_CHEMICAL));
    testPrediction.getProducts().get(0).setId(PRODUCT_PRODUCED_ID);

    Function<L2Prediction, L2Prediction> filter = new ReactionsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("One substrate has no ID- should return no reactions", 0, result.getReactionCount());
  }
}
