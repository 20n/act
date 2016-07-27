package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Reaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ReactionsTransformer implements Function<L2Prediction, L2Prediction> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionsTransformer.class);

  private final MongoDB mongoDB;

  public ReactionsTransformer(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Looks up prediction in reactions DB, and dds any reactions found in the DB that match all substrates
   * and products of the prediction.
   *
   * @param prediction The prediction to be tested.
   * @return The modified prediction.
   */
  public L2Prediction apply(L2Prediction prediction) {

    // Return unmodified prediction if there are no substrate ids or no product ids, or if some
    // of the substrate or product inchis were not found in the DB.
    if (prediction.getSubstrateIds().isEmpty() ||
        prediction.getProductIds().isEmpty() ||
        prediction.getSubstrateIds().size() < prediction.getSubstrates().size() ||
        prediction.getProductIds().size() < prediction.getProducts().size()) {
      return prediction;
    }

    // Get reactions that match all substrates and products.
    List<Long> substrateIds = prediction.getSubstrateIds();
    List<Long> productIds = prediction.getProductIds();

    List<Reaction> reactionsFromDB = mongoDB.getRxnsWithAll(substrateIds, productIds);

    // Bin reactions based on whether they match the prediction RO
    List<Long> reactionsRoMatch = new ArrayList<Long>();
    List<Long> reactionsNoRoMatch = new ArrayList<Long>();

    for (Reaction reaction : reactionsFromDB) {
      if (reactionMatchesRo(reaction, prediction.getRo().getId())) {
        reactionsRoMatch.add(new Long(reaction.getUUID()));
      } else {
        reactionsNoRoMatch.add(new Long(reaction.getUUID()));
      }
    }

    // Add reaction lists to prediction and return
    prediction.setReactionsRoMatch(reactionsRoMatch);
    prediction.setReactionsNoRoMatch(reactionsNoRoMatch);
    return prediction;
  }

  /**
   * Checks if a Reaction has a given Ro among its mechanistic validator results
   *
   * @param roId The RO id to look for
   * @param reaction The Reaction to look in
   * @return True if the given RO ID is found
   */
  private boolean reactionMatchesRo(Reaction reaction, Integer roId) {

    if (reaction.getMechanisticValidatorResult() != null) {

      Set<String> validatorResults = reaction.getMechanisticValidatorResult().keySet();
      return validatorResults.contains(roId.toString());

    } else {
      return false; // Consider mismatch if there are no validator results at all.
    }
  }

}
