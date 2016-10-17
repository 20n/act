package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import act.shared.Reaction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

  @JsonProperty("orgs")
  Set<String> orgs;

  @JsonCreator
  public NetworkEdge(
      @JsonProperty("substrates") List<String> substrates,
      @JsonProperty("products") List<String> products,
      @JsonProperty("reaction_ids") Set<Integer> reactionIds,
      @JsonProperty("projector_names") Set<String> projectorNames,
      @JsonProperty("orgs") Set<String> orgs) {
    if (substrates == null || products == null) {
      throw new IllegalArgumentException("Cannot create network edge with null substrates or products.");
    }
    this.substrates = substrates;
    this.products = products;
    this.reactionIds = reactionIds;
    this.projectorNames = projectorNames;
    this.orgs = orgs;
  }

  public NetworkEdge(List<String> substrates, List<String> products) {
    this(substrates, products, new HashSet<Integer>(), new HashSet<String>(), new HashSet<String>());
  }

  public static NetworkEdge buildEdgeFromReaction(MongoDB db, long rxnId) {
    Reaction reaction = db.getReactionFromUUID(rxnId);
    List<Long> substrateIds = Arrays.asList(reaction.getSubstrates());
    List<String> substrates = substrateIds.stream().map(id -> db.getChemicalFromChemicalUUID(id).getInChI())
        .collect(Collectors.toList());

    List<Long> productIds = Arrays.asList(reaction.getProducts());
    List<String> products = productIds.stream().map(id -> db.getChemicalFromChemicalUUID(id).getInChI())
        .collect(Collectors.toList());

    NetworkEdge edge = new NetworkEdge(substrates, products);
    edge.addReactionId(reaction.getUUID());
    return edge;
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
   * regardless of auxiliary data. This takes into account both the identity and cardinality of each chemical in the lists.
   *
   * @param edge The edge to compare to.
   * @return True if same.
   */
  public boolean hasSameChemicals(NetworkEdge edge) {
    return CollectionUtils.isEqualCollection(this.getSubstrates(), edge.getSubstrates())
        && CollectionUtils.isEqualCollection(this.getProducts(), edge.getProducts());
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

  public void addReactionId(Integer reactionId) {
    this.reactionIds.add(reactionId);
  }

  public Set<String> getProjectorNames() {
    return Collections.unmodifiableSet(projectorNames);
  }

  public void addProjectorName(String projectorName) {
    this.projectorNames.add(projectorName);
  }

  public Set<String> getOrgs() {
    return this.orgs;
  }

  public void addOrg(String org) {
    this.orgs.add(org);
  }
}

