package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Reaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class ReactionsFilter implements Function<L2Prediction, List<L2Prediction>> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionsFilter.class);

  private final MongoDB mongoDB;

  public ReactionsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters prediction based on lookup in reactions DB.
   * Keeps the prediction if it has at least one substrate and product.
   * Adds any reactions found in the DB that match all substrates and products of the prediction
   *
   * @param prediction the prediction to be tested.
   * @return A collection containing the zero or one resulting predictions.
   */
  public List<L2Prediction> apply(L2Prediction prediction) {

    List<L2Prediction> resultList = new ArrayList<L2Prediction>();

    // Return empty list if there are no substrates or no products.
    if (prediction.getSubstrateIds().size() < 1 || prediction.getProductIds().size() < 1) {
      LOGGER.warn("Either substrates or products is empty. Returning empty list of predictions.");
      return resultList;
    }

    // Get reactions that match all substrates and products.
    List<Reaction> reactionsFromDB = mongoDB.getRxnsWithAll(
            prediction.getSubstrateIds(), prediction.getProductIds());

    // Bin reactions based on whether they match the prediction RO
    List<Long> reactionsRoMatch = new ArrayList<Long>();
    List<Long> reactionsNoRoMatch = new ArrayList<Long>();

    for (Reaction reaction : reactionsFromDB) {
      if (reactionMatchesRo(reaction, prediction.getRO().getId())) {
        reactionsRoMatch.add(new Long(reaction.getUUID()));
      } else {
        reactionsNoRoMatch.add(new Long(reaction.getUUID()));
      }
    }

    // Add reaction lists to prediction
    prediction.setReactionsRoMatch(reactionsRoMatch);
    prediction.setReactionsNoRoMatch(reactionsNoRoMatch);

    // Add prediction to result and return
    resultList.add(prediction);
    return resultList;
  }

  /**
   * Checks if a Reaction has a given Ro among its mechanistic validator results
   *
   * @param roId     The RO id to look for
   * @param reaction The Reaction to look in
   * @return True if the given RO ID is found
   */
  private boolean reactionMatchesRo(Reaction reaction, Integer roId) {

    if (reaction.getMechanisticValidatorResult() != null) {

      Iterator<String> validatorResults = reaction.getMechanisticValidatorResult().keys();
      boolean match = false; // Default to mismatch, if no match found.
      String roString;

      while (validatorResults.hasNext()) {
        roString = validatorResults.next();
        if (roId == Integer.parseInt(roString)) {
          match = true; // Matches if any one of the mechanistic validator results matches.
        }
      }

      return match;

    } else {
      return false; // Consider mismatch if there are no validator results at all.
    }
  }
}