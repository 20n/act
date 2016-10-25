package com.act.lcms.v2;

import com.act.biointerpretation.networkanalysis.Metabolite;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by tom on 10/25/16.
 */
public class DetectedPeak {

  private Double mz;
  private Double retentionTime;
  private Double mzWindow;
  private Double retentionTimeWindow;
  private Double moleculeMass;
  private List<Metabolite> metabolites;

  public void parseFromJsonNode(JsonNode node, Map<Long, List<List<String>>> matchingInchis) {
    this.mz = node.get("mz").asDouble();
    this.retentionTime = node.get("rt").asDouble();
    this.mzWindow = node.get("mz_band").asDouble();
    this.retentionTimeWindow = node.get("rt_band").asDouble();
    this.moleculeMass = node.get("moleculeMass").asDouble();
    this.metabolites = matchingInchis.get(node.get("matching_inchis").asLong())
        .stream().map(namedInchis -> new Metabolite(namedInchis.get(0))).collect(Collectors.toList());
  }
}
