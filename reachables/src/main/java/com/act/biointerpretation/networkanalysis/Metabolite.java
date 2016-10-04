package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a metabolite in the metabolite network.
 * For now this class only stores an inchi, but in the future it can represent multiple levels of abstraction: a
 * structure in any format, a chemical formula, or only a mass.
 */
public class Metabolite {

  @JsonProperty("inchi")
  private String inchi;

  public Metabolite(String inchi) {
    this.inchi = inchi;
  }

  public String getInchi() {
    return inchi;
  }
}
