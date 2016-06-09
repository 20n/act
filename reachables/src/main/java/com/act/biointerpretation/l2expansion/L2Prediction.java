package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single predicted reaction from the L2 expansion
 */
public class L2Prediction {

  @JsonProperty("substrates")
  List<String> substrateInchis;

  @JsonProperty("ro")
  Ero ro;

  @JsonProperty("products")
  List<String> productInchis;

  public L2Prediction(List<String> substrateInchis, Ero ro, List<String> productInchis) {
    this.substrateInchis = substrateInchis;
    this.ro = ro;
    this.productInchis = productInchis;
  }

  public List<String> getSubstrateInchis() {
    return substrateInchis;
  }

  public Ero getRO() {
    return ro;
  }

  public List<String> getProductInchis() {
    return productInchis;
  }

  /**
   * @param mongoDB The DB in which to query the chemicals.
   * @return True if all substrates in this prediction are found in the DB.
   */
  public boolean substratesInChemicalDB(MongoDB mongoDB){
    for (String inchi: substrateInchis){
      if(mongoDB.getChemicalFromInChI(inchi) == null){
        return false;
      }
    }
    return true;
  }

  /**
   *
   * @param mongoDB The DB in which to query the chemicals.
   * @return True if all products in this prediction are found in the DB.
   */
  public boolean productsInChemicalDB(MongoDB mongoDB){
    for (String inchi: productInchis){
      if(mongoDB.getChemicalFromInChI(inchi) == null){
        return false;
      }
    }
    return true;
  }
}
