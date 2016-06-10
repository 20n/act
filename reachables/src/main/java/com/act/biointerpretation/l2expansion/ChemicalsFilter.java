package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

public class ChemicalsFilter implements Function<L2Prediction, List<L2Prediction>> {

  private MongoDB mongoDB;

  public ChemicalsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters prediction by looking up its substrates and products in DB.
   * Returns an empty list if any chemical does not exist; otherwise returns a list containing the
   * original prediction, with substrate and product ids added.
   *
   * @param prediction The prediction to be tested.
   * @return The modified prediction, or an empty list.
   */
  public List<L2Prediction> apply(L2Prediction prediction) {

    List<L2Prediction> resultList = new ArrayList<L2Prediction>();

    // Add product chemical ids.
    for (String inchi : prediction.getProductInchis()) {
      Chemical product = mongoDB.getChemicalFromInChI(inchi);
      if (product != null) {
        prediction.addProductId(product.getUuid());
      } else {
        return resultList; // Empty list.
      }
    }

    // Add substrate chemical ids.
    for (String inchi : prediction.getSubstrateInchis()) {
      Chemical substrate = mongoDB.getChemicalFromInChI(inchi);
      if (substrate != null) {
        prediction.addSubstrateId(substrate.getUuid());
      } else {
        return resultList; // Empty list.
      }
    }

    // Return list with one prediction, including substrates and products.
    resultList.add(prediction);
    return resultList;
  }
}