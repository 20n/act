package com.act.lcms.v2;

import com.act.biointerpretation.networkanalysis.Metabolite;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class DetectedPeak {

  private Double mz;
  private Double retentionTime;
  private Double mzWindow;
  private Double retentionTimeWindow;
  private Double moleculeMass;
  private List<Metabolite> metabolites;

  public void parseFromJsonNode(JsonNode node, Map<Long, List<String>> matchingInchis) {
    this.mz = node.get("mz").asDouble();
    this.retentionTime = node.get("rt").asDouble();
    this.mzWindow = node.get("mz_band").asDouble();
    this.retentionTimeWindow = node.get("rt_band").asDouble();
    this.moleculeMass = node.get("moleculeMass").asDouble();
    this.metabolites = matchingInchis.get(node.get("matching_inchis").asLong())
        .stream().map(namedInchis -> new Metabolite(namedInchis)).collect(Collectors.toList());
  }

  public Double getMz() {
    return mz;
  }

  public void setMz(Double mz) {
    this.mz = mz;
  }

  public Double getRetentionTime() {
    return retentionTime;
  }

  public void setRetentionTime(Double retentionTime) {
    this.retentionTime = retentionTime;
  }

  public Double getMzWindow() {
    return mzWindow;
  }

  public void setMzWindow(Double mzWindow) {
    this.mzWindow = mzWindow;
  }

  public Double getRetentionTimeWindow() {
    return retentionTimeWindow;
  }

  public void setRetentionTimeWindow(Double retentionTimeWindow) {
    this.retentionTimeWindow = retentionTimeWindow;
  }

  public Double getMoleculeMass() {
    return moleculeMass;
  }

  public void setMoleculeMass(Double moleculeMass) {
    this.moleculeMass = moleculeMass;
  }

  public List<Metabolite> getMetabolites() {
    return metabolites;
  }

  public void setMetabolites(List<Metabolite> metabolites) {
    this.metabolites = metabolites;
  }
}
