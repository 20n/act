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

import com.act.lcms.v2.Metabolite;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a node, or chemical, in the metabolism network
 */
public class NetworkNode {

  private static AtomicInteger uidCounter = new AtomicInteger(0);

  @JsonProperty("uid")
  private final Integer UID;

  @JsonProperty("metabolite")
  private Metabolite metabolite;

  private Set<NetworkEdge> outEdges;

  private Set<NetworkEdge> inEdges;

  @JsonCreator
  private NetworkNode(@JsonProperty("metabolite") Metabolite metabolite,
                      @JsonProperty("uid") Integer UID) {
    this.outEdges = new HashSet<>();
    this.inEdges = new HashSet<>();
    this.metabolite = metabolite;
    this.UID = UID;
    uidCounter.set(Math.max(UID + 1, uidCounter.get()));
  }

  public NetworkNode(Metabolite metabolite) {
    this.outEdges = new HashSet<>();
    this.inEdges = new HashSet<>();
    this.metabolite = metabolite;
    this.UID = uidCounter.getAndIncrement();
  }

  public Metabolite getMetabolite() {
    return metabolite;
  }

  public Integer getUID() {
    return UID;
  }

  /**
   * Get out edges.
   * Don't need to explicitly serialize these because connectivity information is all contained in the edge -> node pointers.
   * The node -> edge pointers can thus be reconstructed on deserialization.
   */
  @JsonIgnore
  public Collection<NetworkEdge> getOutEdges() {
    return Collections.unmodifiableSet(outEdges);
  }

  public void addOutEdge(NetworkEdge edge) {
    this.outEdges.add(edge);
  }

  /**
   * Get in edges.
   * Don't need to explicitly serialize these because connectivity information is all contained in the edge -> node pointers.
   * The node -> edge pointers can thus be reconstructed on deserialization.
   */
  @JsonIgnore
  public Collection<NetworkEdge> getInEdges() {
    return Collections.unmodifiableSet(inEdges);
  }

  public void addInEdge(NetworkEdge edge) {
    this.inEdges.add(edge);
  }
}
