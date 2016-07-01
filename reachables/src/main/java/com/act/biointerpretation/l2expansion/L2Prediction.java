package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a single predicted reaction from the L2 expansion
 */
public class L2Prediction {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("_id")
  Integer id;

  @JsonProperty("substrates")
  List<L2PredictionChemical> substrates;

  @JsonProperty("ro")
  L2PredictionRo ro;

  @JsonProperty("products")
  List<L2PredictionChemical> products;

  @JsonProperty("reactions_ro_match")
  List<Long> reactionsRoMatch;

  @JsonProperty("reactions_no_ro_match")
  List<Long> reactionsNoRoMatch;

  // Necessary for JSON reading
  private L2Prediction() {
  }

  public L2Prediction(L2Prediction template) {
    this.id = template.id;

    this.substrates = new ArrayList<>();
    for (L2PredictionChemical substrate : template.substrates) {
      this.substrates.add(new L2PredictionChemical(substrate));
    }

    this.ro = new L2PredictionRo(template.ro);

    this.products = new ArrayList<>();
    for (L2PredictionChemical product : template.products) {
      this.products.add(new L2PredictionChemical(product));
    }

    this.reactionsRoMatch = new ArrayList<Long>(template.reactionsRoMatch);
    this.reactionsNoRoMatch = new ArrayList<Long>(template.reactionsNoRoMatch);
  }

  public L2Prediction(Integer id, List<L2PredictionChemical> substrates, L2PredictionRo ro, List<L2PredictionChemical> products) {
    this.id = id;
    this.substrates = substrates;
    this.products = products;
    this.ro = ro;
    this.reactionsRoMatch = new ArrayList<Long>();
    this.reactionsNoRoMatch = new ArrayList<Long>();
  }

  @JsonIgnore
  public int getReactionCount() {
    return reactionsRoMatch.size() + reactionsNoRoMatch.size();
  }

  @JsonIgnore
  public List<String> getSubstrateInchis() {
    List<String> inchis = new ArrayList<>();
    for (L2PredictionChemical chemical : getSubstrates()) {
      inchis.add(chemical.getInchi());
    }
    return inchis;
  }

  @JsonIgnore
  public List<Long> getSubstrateIds() {
    List<Long> ids = new ArrayList<>();
    for (L2PredictionChemical chemical : getSubstrates()) {
      if (chemical.hasId()) {
        ids.add(chemical.getId());
      }
    }
    return ids;
  }

  @JsonIgnore
  public List<String> getSubstrateNames() {
    List<String> names = new ArrayList<>();
    for (L2PredictionChemical chemical : getSubstrates()) {
      if (chemical.hasName()) {
        names.add(chemical.getName());
      }
    }
    return names;
  }

  @JsonIgnore
  public List<String> getProductInchis() {
    List<String> inchis = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      inchis.add(chemical.getInchi());
    }
    return inchis;
  }

  @JsonIgnore
  public List<Long> getProductIds() {
    List<Long> ids = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      if (chemical.hasId()) {
        ids.add(chemical.getId());
      }
    }
    return ids;
  }

  @JsonIgnore
  public List<String> getProductNames() {
    List<String> names = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      if (chemical.hasName()) {
        names.add(chemical.getName());
      }
    }
    return names;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public List<L2PredictionChemical> getSubstrates() {
    return substrates;
  }

  public void setSubstrates(List<L2PredictionChemical> substrates) {
    this.substrates = substrates;
  }

  public List<L2PredictionChemical> getProducts() {
    return products;
  }

  public void setProducts(List<L2PredictionChemical> products) {
    this.products = products;
  }

  public L2PredictionRo getRo() {
    return ro;
  }

  public List<Long> getReactionsRoMatch() {
    return reactionsRoMatch;
  }

  public void setReactionsRoMatch(List<Long> reactionsRoMatch) {
    this.reactionsRoMatch = reactionsRoMatch;
  }

  public List<Long> getReactionsNoRoMatch() {
    return reactionsNoRoMatch;
  }

  public void setReactionsNoRoMatch(List<Long> reactionsNoRoMatch) {
    this.reactionsNoRoMatch = reactionsNoRoMatch;
  }

}
