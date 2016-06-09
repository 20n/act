package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gil on 6/9/16.
 */
public class PredictionFilter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PredictionFilter.class);

  private FilterType filterType;
  private MongoDB mongoDB;

  public enum FilterType {
    SUBSTRATES_IN_DB,
    PRODUCTS_IN_DB,
    REACTION_IN_DB
  }

  public PredictionFilter(FilterType filterType, MongoDB mongoDB) {
    this.mongoDB = mongoDB;
    this.filterType = filterType;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if the prediction passes the test of this filter.
   */
  public boolean test(L2Prediction prediction) {
    switch (filterType) {
      case SUBSTRATES_IN_DB:
        return substratesInChemicalDB(prediction);
      case PRODUCTS_IN_DB:
        return productsInChemicalDB(prediction);
      case REACTION_IN_DB:
        return reactionInReactionsDB(prediction);
      default:
        LOGGER.error("Invalid filter type given; returning false.");
        return false;
    }
  }

  public void setFilterType(FilterType filterType) {
    this.filterType = filterType;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if all of the prediction's substrate chemicals are in the DB.
   */
  private boolean substratesInChemicalDB(L2Prediction prediction) {
    for (String inchi : prediction.getSubstrateInchis()) {
      if (mongoDB.getChemicalFromInChI(inchi) == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if all of the prediction's product chemicals are in the DB.
   */
  private boolean productsInChemicalDB(L2Prediction prediction) {
    for (String inchi : prediction.getProductInchis()) {
      if (mongoDB.getChemicalFromInChI(inchi) == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * Currently only looks for reactions that match first substrate and product
   * TODO: change this to apply to multiple substrates, products
   * @param prediction the prediction to be tested.
   * @return True if there is a reaction in the DB whose substrates and products match this prediction.
   */
  private boolean reactionInReactionsDB(L2Prediction prediction) {
    List<Long> substrateIds = new ArrayList<Long>();
    for (String inchi : prediction.getSubstrateInchis()) {
      Long id = mongoDB.getChemicalFromInChI(inchi).getUuid();
      if(id != null) {
        substrateIds.add(id);
      } else {
        LOGGER.warn("Substrate not found in chemicals DB. Returning false.");
        return false;
      }
    }

    List<Long> productIds = new ArrayList<Long>();
    for (String inchi : prediction.getProductInchis()) {
      Long id = mongoDB.getChemicalFromInChI(inchi).getUuid();
      if(id != null){
        productIds.add(id);
      } else {
        LOGGER.warn("Product not found in chemicals DB. Returning false.");
        return false;
      }
    }

    if (substrateIds.size() > 1) {
      LOGGER.warn("Multiple substrates supplied; using only first one.");
    }
    if (productIds.size() > 1) {
      LOGGER.warn("Multiple products supplied; using only first one.");
    }
    if (substrateIds.size() < 1 || productIds.size() < 1){
      LOGGER.warn("Either substrates or products is empty.  Returning false.");
      return false;
    }

    return mongoDB.getRxnsWith(substrateIds.get(0), productIds.get(0)).size() > 0;
  }
}
