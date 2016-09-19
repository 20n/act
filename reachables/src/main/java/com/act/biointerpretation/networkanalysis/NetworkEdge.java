package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

//Represents an edge, or reaction, in the metabolism network
public class NetworkEdge {

  @JsonProperty("substrate")
  private String substrate;

  @JsonProperty("product")
  private String product;

  @JsonProperty("reaction_id")
  Optional<Integer> reactionId;

  @JsonProperty("projector_name")
  Optional<String> projectorName;

  public NetworkEdge() {
    reactionId = Optional.empty();
    projectorName = Optional.empty();
  }

  public NetworkEdge(String substrate, String product) {
    this();
    this.substrate = substrate;
    this.product = product;
  }

  public String getSubstrate() {
    return substrate;
  }

  public void setSubstrate(String substrate) {
    this.substrate = substrate;
  }

  public String getProduct() {
    return product;
  }

  public void setProduct(String product) {
    this.product = product;
  }

  public Optional<Integer> getReactionId() {
    return reactionId;
  }

  public void setReactionId(int reactionId) {
    this.reactionId = Optional.of(reactionId);
  }

  public Optional<String> getProjectorName() {
    return projectorName;
  }

  public void setProjectorName(String projectorName) {
    this.projectorName = Optional.of(projectorName);
  }
}

