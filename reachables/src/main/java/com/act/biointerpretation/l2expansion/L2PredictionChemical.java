package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one substrate or product prediction in an L2Prediction
 */
public class L2PredictionChemical {

  public static final String NO_NAME = "no_name";
  public static final Long NO_ID = new Long(-1);

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("id")
  private Long id;

  @JsonProperty("name")
  private String name;

  // Necessary for JSON
  private L2PredictionChemical() {
  }

  public L2PredictionChemical(String inchi) {
    this.inchi = inchi;
    this.id = NO_ID;
    this.name = NO_NAME;
  }

  public L2PredictionChemical(String inchi, Long id, String name) {
    this.inchi = inchi;
    this.id = id;
    this.name = name;
  }

  public static List<L2PredictionChemical> getPredictionChemicals(List<String> inchis) {
    List<L2PredictionChemical> results = new ArrayList<>();
    for (String inchi : inchis) {
      results.add(new L2PredictionChemical(inchi));
    }
    return results;
  }

  public String getInchi() {
    return inchi;
  }

  public boolean hasId() {
    return !id.equals(NO_ID);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public boolean hasName() {
    return !name.equals(NO_NAME);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
