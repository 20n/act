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
   * Filters prediction based on lookup in reactions DB.
   * Keeps the prediction if it has at least one substrate and product.
   * Adds any reactions found in the DB that match all substrates and products of the prediction.
   *
   * @param prediction the prediction to be tested.
   * @return A collection containing the zero or one resulting predictions.
   */
  public List<L2Prediction> applyFilter(L2Prediction prediction) {

    List<L2Prediction> resultList = new ArrayList<L2Prediction>();

    // Return empty list if there are no substrates or no products.
    if (prediction.getSubstrateIds().size() < 1 || prediction.getProductIds().size() < 1) {
      LOGGER.warn("Either substrates or products is empty. Returning empty list of predictions.");
      return resultList;
    }

    // Get reactions that match all substrates and products.
    List<Long> reactions = mongoDB.getRxnsWithAll(
            prediction.getSubstrateIds(), prediction.getProductIds());

    // Return list with one prediction, including all reactions matching the prediction.
    prediction.setReactions(reactions);
    resultList.add(prediction);
    return resultList;
  }
}