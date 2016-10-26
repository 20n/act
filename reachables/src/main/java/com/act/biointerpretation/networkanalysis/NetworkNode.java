package com.act.biointerpretation.networkanalysis;

import com.act.lcms.v2.Metabolite;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a node, or chemical, in the metabolism network
 */
public class NetworkNode {

  private static Integer uidCounter = 0;

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
    uidCounter = Math.max(UID + 1, uidCounter);
  }

  public NetworkNode(Metabolite metabolite) {
    this(metabolite, uidCounter);
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
