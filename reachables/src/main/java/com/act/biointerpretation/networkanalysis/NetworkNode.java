package com.act.biointerpretation.networkanalysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

// Represents a node, or chemical, in the metabolism network
public class NetworkNode {

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("out_edges")
  private List<NetworkEdge> outEdges;

  @JsonProperty("in_edges")
  private List<NetworkEdge> inEdges;

  private IonAnalysisInterchangeModel.LCMS_RESULT lcmsResult;

  public NetworkNode(String inchi) {
    this.inchi = inchi;
    this.outEdges = new ArrayList<>();
    this.inEdges = new ArrayList<>();
  }

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

  public IonAnalysisInterchangeModel.LCMS_RESULT getLcmsResult() {
    return lcmsResult;
  }

  public void setLcmsResult(IonAnalysisInterchangeModel.LCMS_RESULT lcmsResult) {
    this.lcmsResult = lcmsResult;
  }

  // For JSON SerDe
  private NetworkNode() {
  }
}
