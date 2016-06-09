package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;

import java.util.function.Predicate;

public class ProductsFilter implements Predicate<L2Prediction> {

  private MongoDB mongoDB;

  public ProductsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * @param prediction The prediction to be tested.
   * @return True if all of the prediction's product chemicals are in the DB.
   */
  public boolean test(L2Prediction prediction) {
    for (String inchi : prediction.getProductInchis()) {
      if (mongoDB.getChemicalFromInChI(inchi) == null) {
        return false;
      }
    }
    return true;
  }

}