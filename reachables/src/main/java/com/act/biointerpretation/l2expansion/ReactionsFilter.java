package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ReactionsFilter implements PredictionFilter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionsFilter.class);

  private final MongoDB mongoDB;

  public ReactionsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters predictions based on lookup in reactions DB
   * Keeps all predictions that have at least one substrate and product.
   * Adds any reactions found in the DB that match the prediction.
   *
   * @param prediction the prediction to be tested.
   * @return A ollection containing the zero or one resulting predictions.
   */
  public List<L2Prediction> applyFilter(L2Prediction prediction) {

    List<L2Prediction> resultList = new ArrayList<L2Prediction>();

    List<Long> substrateIds = prediction.getSubstrateIds();
    List<Long> productIds = prediction.getProductIds();

    if (substrateIds.size() < 1 || productIds.size() < 1) {
      LOGGER.warn("Either substrates or products is empty.  Returning empty list of predictions.");
      return resultList; // Empty list.
    }

    List<Long> reactions = mongoDB.getRxnsWithAll(substrateIds, productIds);
    prediction.setReactions(reactions);

    resultList.add(prediction);
    return resultList; // List with one prediction, including reactions matching the prediction.
  }

  /**
   * Adds the reactions in the DB that match the prediction to the prediction.
   *
   * @param prediction The L2Prediction.
   * @return The L2Prediction, with its matching reactions added.
   */
  public L2Prediction transform(L2Prediction prediction) {
    List<Long> substrateIds = mongoDB.getIdsFromInChIs(prediction.getSubstrateInchis());
    List<Long> productIds = mongoDB.getIdsFromInChIs(prediction.getProductInchis());

    prediction.setReactions(mongoDB.getRxnsWithAll(substrateIds, productIds));

    return prediction;
  }
}