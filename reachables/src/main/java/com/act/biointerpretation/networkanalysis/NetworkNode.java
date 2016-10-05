package com.act.biointerpretation.networkanalysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node, or chemical, in the metabolism network
 */
public class NetworkNode {

  @JsonProperty("metabolite")
  private Metabolite metabolite;

  private List<NetworkEdge> outEdges;

  private List<NetworkEdge> inEdges;

  public NetworkNode(Metabolite metabolite) {
    this.outEdges = new ArrayList<>();
    this.inEdges = new ArrayList<>();
    this.metabolite = metabolite;
  }

  public Metabolite getMetabolite() {
    return metabolite;
  }

  /**
   * Get out edges.
   * Don't need to explicitly serialize these because connectivity information is all contained in the edge -> node pointers.
   * The node -> edge pointers can thus be reconstructed on deserialization.
   */
  @JsonIgnore
  public List<NetworkEdge> getOutEdges() {
    return Collections.unmodifiableList(outEdges);
  }

  public void setOutEdges(List<NetworkEdge> outEdges) {
    this.outEdges = outEdges;
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
  public List<NetworkEdge> getInEdges() {
    return Collections.unmodifiableList(inEdges);
  }

  public void setInEdges(List<NetworkEdge> inEdges) {
    this.inEdges = inEdges;
  }

  public void addInEdge(NetworkEdge edge) {
    this.inEdges.add(edge);
  }

  // For JSON SerDe
  private NetworkNode() {
    this(null);
  }
}
