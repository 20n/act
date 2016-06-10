package com.act.biointerpretation.l2expansion;

import com.act.biointerpretation.mechanisminspection.Ero;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a single predicted reaction from the L2 expansion
 */
public class L2Prediction {

  @JsonProperty("substrate_inchis")
  final List<String> substrateInchis;

  @JsonProperty("ro")
  final Ero ro;

  @JsonProperty("product_inchis")
  final List<String> productInchis;

  @JsonProperty("substrate_ids")
  List<Long> substrateIds;

  @JsonProperty("product_ids")
  List<Long> productIds;

  @JsonProperty("reactions_ro_match")
  List<Long> reactionsRoMatch;

  @JsonProperty("reactions_no_ro_match")
  List<Long> reactionsNoRoMatch;

  public L2Prediction(List<String> substrateInchis, Ero ro, List<String> productInchis) {
    this.substrateInchis = substrateInchis;
    this.ro = ro;
    this.productInchis = productInchis;
    this.reactionsRoMatch = new ArrayList<Long>();
    this.reactionsNoRoMatch = new ArrayList<Long>();
    this.substrateIds = new ArrayList<Long>();
    this.productIds = new ArrayList<Long>();
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

  public List<Long> getSubstrateIds() {
    return substrateIds;
  }

  public void addSubstrateId(Long substrateId) {
    this.substrateIds.add(substrateId);
  }

  public List<Long> getProductIds() {
    return productIds;
  }

  public void addProductId(Long productId) {
    this.productIds.add(productId);
  }
}
