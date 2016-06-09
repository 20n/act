package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
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
   * TODO: change this to apply to multiple substrates, products
   * @param prediction the prediction to be tested.
   * @return True if there is a reaction in the DB whose substrates and products match the first substrate
   * and product in this prediction.
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

    if (substrateIds.size() > 1) {
      LOGGER.warn("Multiple substrates supplied; using only first one.");
    }
    if (productIds.size() > 1) {
      LOGGER.warn("Multiple products supplied; using only first one.");
    }

    return mongoDB.getRxnsWith(substrateIds.get(0), productIds.get(0)).size() > 0;
  }

  /**
   * Use DB to transform inchis into chemical DB ids
   * @param inchis A list of inchis to transform.
   * @return The corresponding chemical DB ids.
   */
  private List<Long> getChemicalIds(List<String> inchis){
    List<Long> results = new ArrayList<Long>();
    for (String inchi : inchis) {
      Long id = mongoDB.getChemicalFromInChI(inchi).getUuid();
      if(id != null){
        results.add(id);
      }
    }
    return results;
  }
}