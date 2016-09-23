package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an edge, or reaction, in the metabolism network
 */
public class NetworkEdge {

  @JsonProperty("substrate")
  private String substrate;

  @JsonProperty("product")
  private String product;

  @JsonProperty("reaction_ids")
  Set<Integer> reactionIds;

  @JsonProperty("projector_names")
  Set<String> projectorNames;

  public NetworkEdge() {
    this(null, null);
  }

  public NetworkEdge(String substrate, String product) {
    reactionIds = new HashSet<>();
    projectorNames = new HashSet<>();
    this.substrate = substrate;
    this.product = product;
  }

  /**
   * Adds auxiliary data from another edge to this edge.
   * This includes reaction IDs and projector names associated with the other edge.
   * Used to merge two edges with the same substrate, product, but different auxiliary data.
   *
   * @param edge The edge whose data should be added.
   */
  public void addDataFrom(NetworkEdge edge) {
    this.reactionIds.addAll(edge.getReactionIds());
    this.projectorNames.addAll(edge.getProjectorNames());
  }

  /**
   * Tests if this edge should be considered the same as another. Returns true if substrate and product are the same,
   * regardless of auxiliary data.
   *
   * @param edge The edge to compare to.
   * @return True if same.
   */
  public boolean hasSameChemicals(NetworkEdge edge) {
    return this.substrate.equals(edge.getSubstrate()) && this.product.equals(edge.getProduct());
  }

  public String getSubstrate() {
    return substrate;
  }

  public String getProduct() {
    return product;
  }

  public Set<Integer> getReactionIds() {
    return Collections.unmodifiableSet(reactionIds);
  }

  public void setReactionIds(Set<Integer> reactionIds) {
    this.reactionIds = reactionIds;
  }

  public void addReactionId(Integer reactionId) {
    this.reactionIds.add(reactionId);
  }

  public Set<String> getProjectorNames() {
    return Collections.unmodifiableSet(projectorNames);
  }

  public void setProjectorNames(Set<String> projectorNames) {
    this.projectorNames = projectorNames;
  }

  public void addProjectorName(String projectorName) {
    this.projectorNames.add(projectorName);
  }
}

