package com.act.biointerpretation.l2expansion;

import com.act.biointerpretation.mechanisminspection.Ero;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.BufferedWriter;
import java.io.IOException;


/*
 * Represents a single predicted reaction from the L2 expansion
 */
public class L2Prediction {

  @JsonProperty("substrates")
  String substrateInchis[];

  @JsonProperty("ro")
  Ero ro;

  @JsonProperty("products")
  String productInchis[];

  public L2Prediction(String[] substrateInchis, Ero ro, String[] productInchis) {
    this.substrateInchis = substrateInchis;
    this.ro = ro;
    this.productInchis = productInchis;
  }

  public String[] getSubstrateInchis() {
    return substrateInchis;
  }

  public Ero getRO() {
    return ro;
  }

  public String[] getProductInchis() {
    return productInchis;
  }

  public void printPrediction(BufferedWriter writer) throws IOException {
    writer.write("RO ID, string:\n" + ro.getId() + "," + ro.getRo() + "\n");

    writer.write("Reactants:\n");
    for (String inchi : substrateInchis) {
      writer.write(inchi + "\n");
    }

    writer.write("Products:\n");
    for (String inchi : productInchis) {
      writer.write(inchi + "\n");
    }
  }
}
