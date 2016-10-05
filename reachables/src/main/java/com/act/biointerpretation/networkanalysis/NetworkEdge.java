package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an edge, or reaction, in the metabolism network
 */
public class NetworkEdge {

  @JsonProperty("substrates")
  private List<String> substrates;

  @JsonProperty("products")
  private List<String> products;

  @JsonProperty("reaction_ids")
  Set<Integer> reactionIds;

  @JsonProperty("projector_names")
  Set<String> projectorNames;

  public NetworkEdge() {
    this(null, null);
  }

  public NetworkEdge(List<String> substrates, List<String> products) {
    reactionIds = new HashSet<>();
    projectorNames = new HashSet<>();

    if (substrates == null || products == null) {
      throw new IllegalArgumentException("Cannot create network edge with null substrates or products.");
    }

    this.substrates = substrates;
    this.products = products;
  }

  /**
   * Merges another edge into this one by adding the auxiliary data from the other edge to this edge.
   * This includes reaction IDs and projector names associated with the other edge.
   * Ensures that the edges have the same chemicals; throws IllegalArgumentException otherwise.
   *
   * @param edge The edge whose data should be added.
   * @throws IllegalArgumentException if the other edge has different chemicals from this one.
   */
  public void merge(NetworkEdge edge) {
    if (!hasSameChemicals(edge)) {
      throw new IllegalArgumentException("Can only merge two edges with the same chemicals.");
    }
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
    return this.substrates.equals(edge.getSubstrates()) && this.products.equals(edge.getProducts());
  }

  public List<String> getSubstrates() {
    return substrates;
  }

  public List<String> getProducts() {
    return products;
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

