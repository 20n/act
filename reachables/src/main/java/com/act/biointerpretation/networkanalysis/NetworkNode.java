package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

// Represents a node, or chemical, in the metabolism network
public class NetworkNode {

  public NetworkNode(String inchi) {
    this.inchi = inchi;
    this.outEdges = new ArrayList<>();
    this.inEdges = new ArrayList<>();
  }

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("out_edges")
  private List<NetworkEdge> outEdges;

  @JsonProperty("in_edges")
  private List<NetworkEdge> inEdges;

  public String getInchi() {
    return inchi;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public List<NetworkEdge> getOutEdges() {
    return outEdges;
  }

  public void setOutEdges(List<NetworkEdge> outEdges) {
    this.outEdges = outEdges;
  }

  public void addOutEdge(NetworkEdge edge) {
    this.outEdges.add(edge);
  }

  public List<NetworkEdge> getInEdges() {
    return inEdges;
  }

  public void setInEdges(List<NetworkEdge> inEdges) {
    this.inEdges = inEdges;
  }

  public void addInEdge(NetworkEdge edge) {
    this.inEdges.add(edge);
  }

  // Only for json reading
  private NetworkNode() {}
}
