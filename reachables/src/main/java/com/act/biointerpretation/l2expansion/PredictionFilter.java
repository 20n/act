package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by gil on 6/9/16.
 */
public class PredictionFilter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PredictionFilter.class);

  private FilterType filterType;
  private MongoDB mongoDB;

  public enum FilterType {
    SUBSTRATES_IN_DB,
    PRODUCTS_IN_DB
  };

  public PredictionFilter(FilterType filterType, MongoDB mongoDB){
    this.mongoDB = mongoDB;
    this.filterType = filterType;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if the prediction passes the test of this filter.
   */
  public boolean test(L2Prediction prediction){
    switch(filterType){
      case SUBSTRATES_IN_DB:
        return substratesInChemicalDB(prediction);
      case PRODUCTS_IN_DB:
        return productsInChemicalDB(prediction);
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
  private boolean substratesInChemicalDB(L2Prediction prediction){
    for (String inchi: prediction.getSubstrateInchis()){
      if(mongoDB.getChemicalFromInChI(inchi) == null){
        return false;
      }
    }
    return true;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if all of the prediction's product chemicals are in the DB.
   */
  private boolean productsInChemicalDB(L2Prediction prediction){
    for (String inchi: prediction.getProductInchis()){
      if(mongoDB.getChemicalFromInChI(inchi) == null){
        return false;
      }
    }
    return true;
  }
}
