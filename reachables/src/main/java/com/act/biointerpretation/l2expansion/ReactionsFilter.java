package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Reaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class ReactionsFilter implements PredictionFilter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SubstratesFilter.class);

  private final MongoDB mongoDB;

  public ReactionsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters predictions based on lookup in reactions DB
   *
   * @param prediction the prediction to be tested.
   * @return True if there is a reaction in the DB whose substrates and products contain all predicted
   * substrates and products.
   */
  public boolean test(L2Prediction prediction) {

    List<Long> substrateIds = mongoDB.getIdsFromInChIs(prediction.getSubstrateInchis());
    List<Long> productIds = mongoDB.getIdsFromInChIs(prediction.getProductInchis());
    Integer predictionRo = prediction.getRO().getId();

    if (substrateIds.size() < prediction.getSubstrateInchis().size()) {
      LOGGER.warn("At least one substrate not found in DB. Returning false.");
      return false;
    }
    if (productIds.size() < prediction.getProductInchis().size()) {
      LOGGER.warn("At least one product not found in DB. Returning false.");
      return false;
    }
    if (substrateIds.size() < 1 || productIds.size() < 1) {
      LOGGER.warn("Either substrates or products is empty.  Returning false.");
      return false;
    }


    List<Long> reactions = mongoDB.getRxnsWithAll(substrateIds, productIds);

    // Not essential for the test- provides sanity check that ROs are working properly
    for (Long reactionId : reactions) {
      if (!roMatchesDB(reactionId, predictionRo)) {
        LOGGER.info("RO %d not found in predicted reaction %d.", predictionRo, reactionId);
      }
    }

    return reactions.size() > 0;
  }

  /**
   * Checks a reaction in the DB to see if its MechanisticValidatorResults includes the RO used to predict it.
   * This should return true, unless there is no MechanisticValidatorResult for the reaction
   * @param reactionId The reaction to test.
   * @param predictionRo The RO used to predict the reaction.
   * @return True if the reaction in the DB is already associatedc with the given RO.
   */
  private boolean roMatchesDB(Long reactionId, Integer predictionRo){
    Reaction reaction = mongoDB.getReactionFromUUID(reactionId);
    if (!(reaction.getMechanisticValidatorResult() == null)) {

      boolean matchedRO = false;
      Iterator<String> matchedROs = reaction.getMechanisticValidatorResult().keys();

      while (matchedROs.hasNext()) {
        Integer foundRo = Integer.parseInt(matchedROs.next());
        if (predictionRo.equals(foundRo)) {
          matchedRO = true;
        }
      }

      return matchedRO;
    }
    return false;
  }
}