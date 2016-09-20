package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

//Represents an edge, or reaction, in the metabolism network
public class NetworkEdge {

  @JsonProperty("substrate")
  private String substrate;

  @JsonProperty("product")
  private String product;

  Optional<Integer> reactionId;
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

  @JsonProperty("reaction_id")
  public void setReactionId(int reactionId) {
    this.reactionId = Optional.of(reactionId);
  }

  public Optional<String> getProjectorName() {
    return projectorName;
  }

  @JsonProperty("projector_name")
  public void setProjectorName(String projectorName) {
    this.projectorName = Optional.of(projectorName);
  }

  @JsonProperty("reaction_id")
  private Integer getReactionIdAsInt() {
    return reactionId.isPresent() ? reactionId.get() : null;
  }


  @JsonProperty("projector_name")
  private String getProjectorNameAsString() {
    return projectorName.isPresent() ? projectorName.get() : null;
  }
}

