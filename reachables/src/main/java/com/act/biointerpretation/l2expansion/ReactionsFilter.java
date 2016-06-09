package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ReactionsFilter implements Predicate<L2Prediction> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SubstratesFilter.class);

  private MongoDB mongoDB;

  public ReactionsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters predictions based on lookup in reactions DB
   * @param prediction the prediction to be tested.
   * @return True if there is a reaction in the DB whose substrates and products contain all predicted
   * substrates and products.
   */
  public boolean test(L2Prediction prediction) {

    List<Long> substrateIds = getChemicalIds(prediction.getSubstrateInchis());
    List<Long> productIds = getChemicalIds(prediction.getProductInchis());

    if(substrateIds.size() < prediction.getSubstrateInchis().size()){
      LOGGER.warn("At least one substrate not found in DB. Returning false.");
      return false;
    }
    if(productIds.size() < prediction.getProductInchis().size()){
      LOGGER.warn("At least one product not found in DB. Returning false.");
      return false;
    }
    if (substrateIds.size() < 1 || productIds.size() < 1){
      LOGGER.warn("Either substrates or products is empty.  Returning false.");
      return false;
    }

    return mongoDB.getRxnsWithAll(substrateIds, productIds).size() > 0;
  }

  /**
   * Use DB to transform inchis into chemical ids.
   * @param inchis A list of inchis to transform.
   * @return The corresponding chemical ids.
   */
  private List<Long> getChemicalIds(List<String> inchis){
    List<Long> results = new ArrayList<Long>();
    for (String inchi : inchis) {
      Chemical chemical = mongoDB.getChemicalFromInChI(inchi);
      if(chemical != null){
        results.add(chemical.getUuid());
      }
    }
    return results;
  }
}