/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an edge, or reaction, in the metabolism network
 */
public class NetworkEdge {

  @JsonProperty("substrates")
  private List<Integer> substrates;

  @JsonProperty("products")
  private List<Integer> products;

  @JsonProperty("reaction_ids")
  Set<Integer> reactionIds;

  @JsonProperty("projector_names")
  Set<String> projectorNames;

  @JsonProperty("orgs")
  Set<String> orgs;

  @JsonCreator
  public NetworkEdge(
      @JsonProperty("substrates") List<Integer> substrates,
      @JsonProperty("products") List<Integer> products,
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

  public NetworkEdge(List<Integer> substrates, List<Integer> products) {
    this(substrates, products, new HashSet<Integer>(), new HashSet<String>(), new HashSet<String>());
  }

  /**
   * Merges another edge into this one by adding the auxiliary data from the other edge to this edge.
   * This includes reaction IDs and projector names associated with the other edge.
   * Ensures that the edges have the same chemicals; throws IllegalArgumentException otherwise.
   *
   * @param edge The edge whose data should be added.
   * @return This edge for chaining.
   * @throws IllegalArgumentException if the other edge has different chemicals from this one.
   */
  public NetworkEdge merge(NetworkEdge edge) {
    if (!hasSameSubstratesAndProducts(edge)) {
      throw new IllegalArgumentException("Can only merge two edges with the same chemicals.");
    }
    this.reactionIds.addAll(edge.getReactionIds());
    this.projectorNames.addAll(edge.getProjectorNames());
    return this;
  }

  /**
   * Tests if this edge should be considered the same as another. Returns true if substrate and product are the same,
   * regardless of auxiliary data. This takes into account both the identity and cardinality of each chemical in the lists.
   *
   * @param edge The edge to compare to.
   * @return True if same.
   */
  public boolean hasSameSubstratesAndProducts(NetworkEdge edge) {
    return CollectionUtils.isEqualCollection(this.getSubstrates(), edge.getSubstrates())
        && CollectionUtils.isEqualCollection(this.getProducts(), edge.getProducts());
  }

  public List<Integer> getSubstrates() {
    return substrates;
  }

  public List<Integer> getProducts() {
    return products;
  }

  public Set<Integer> getReactionIds() {
    return Collections.unmodifiableSet(reactionIds);
  }

  public NetworkEdge addReactionId(Integer reactionId) {
    this.reactionIds.add(reactionId);
    return this;
  }

  public Set<String> getProjectorNames() {
    return Collections.unmodifiableSet(projectorNames);
  }

  public NetworkEdge addProjectorName(String projectorName) {
    this.projectorNames.add(projectorName);
    return this;
  }

  public Set<String> getOrgs() {
    return this.orgs;
  }

  public void addOrg(String org) {
    this.orgs.add(org);
  }
}

