package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;

import java.util.Optional;
import java.util.function.Function;

public class ChemicalsFilter implements Function<L2Prediction, Optional<L2Prediction>> {

  private MongoDB mongoDB;

  public ChemicalsFilter(MongoDB mongoDB) {
    this.mongoDB = mongoDB;
  }

  /**
   * Filters prediction by looking up its substrates and products in DB.
   * Returns an empty list if any chemical does not exist; otherwise returns a list containing the
   * original prediction, with substrate and product ids added.
   * TODO: If performance becomes an issue, cache an inchi->ID map to avoid redundant database queries.
   *
   * @param prediction The prediction to be tested.
   * @return The modified prediction, or an empty list.
   */
  public Optional<L2Prediction> apply(L2Prediction prediction) {

    // Add product chemical ids.
    for (String inchi : prediction.getProductInchis()) {
      Chemical product = mongoDB.getChemicalFromInChI(inchi);
      if (product != null) {
        prediction.addProductId(inchi, product.getUuid());
        prediction.addProductName(inchi, product.getFirstName());
      } else {
        return Optional.empty();
      }
    }

    // Add substrate chemical ids.
    for (String inchi : prediction.getSubstrateInchis()) {
      Chemical substrate = mongoDB.getChemicalFromInChI(inchi);
      if (substrate != null) {
        prediction.addSubstrateId(inchi, substrate.getUuid());
      } else {
        return Optional.empty();
      }
    }

    // Return list with one prediction, including substrates and products.
    return Optional.of(prediction);
  }
}
