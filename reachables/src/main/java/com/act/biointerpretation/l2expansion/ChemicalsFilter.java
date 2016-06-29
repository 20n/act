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
    for (L2PredictionChemical predictedChemical : prediction.getProducts()) {
      String inchi = predictedChemical.getInchi();
      Chemical product = mongoDB.getChemicalFromInChI(inchi);
      if (product != null) {
        predictedChemical.setId(product.getUuid());
        predictedChemical.setName(product.getFirstName());
      }
    }

    // Add substrate chemical ids.
    for (L2PredictionChemical predictedChemical : prediction.getSubstrates()) {
      String inchi = predictedChemical.getInchi();
      Chemical substrate = mongoDB.getChemicalFromInChI(inchi);
      if (substrate != null) {
        predictedChemical.setId(substrate.getUuid());
        predictedChemical.setName(substrate.getFirstName());
      }
    }

    // Return the prediction, including substrate and product ids and names.
    return Optional.of(prediction);
  }
}
