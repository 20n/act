package com.act.lcms.v2;

import com.act.biointerpretation.networkanalysis.Metabolite;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
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

    List<String> matchingInchisList = matchingInchis.get(node.get("matching_inchis").asLong());
    Double moleculeMass = node.get("moleculeMass").asDouble();
    if (matchingInchisList.size() > 0) {
      this.metabolites = matchingInchis.get(node.get("matching_inchis").asLong())
          .stream().map(namedInchis -> new Metabolite(moleculeMass, cleanInchi(namedInchis))).collect(Collectors.toList());
    } else {
      this.metabolites = new ArrayList<>();
      this.metabolites.add(new Metabolite(moleculeMass));
    }
  }

  public String cleanInchi(String inchi){
    return inchi.replace("\"", "").trim();
  }

  public List<Metabolite> getMetabolites() {
    return metabolites;
  }
}
