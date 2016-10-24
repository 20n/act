package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Group all the data relating to a single precursor report.  The report contains a target metabolite,
 * a MetabolismNetwork containing its precursors, and a Map associating each node to its level in the backwards
 * BF search tree.
 * TODO: add LCMS data to the report
 */
public class PrecursorReport {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("target")
  Metabolite target;

  @JsonProperty("network")
  MetabolismNetwork network;

  @JsonProperty("level_map")
  Map<NetworkNode, Integer> levelMap;

  public PrecursorReport(
      Metabolite target,
      MetabolismNetwork network,
      Map<NetworkNode, Integer> levelMap) {
    this.target = target;
    this.network = network;
    this.levelMap = levelMap;
  }

  public Metabolite getTarget() {
    return target;
  }

  @JsonProperty("network")
  public MetabolismNetwork getNetwork() {
    return network;
  }

  @JsonIgnore
  public Map<NetworkNode, Integer> getLevelMap() {
    return levelMap;
  }

  /**
   * Check if a given node is a precursor of the target node. This is not always the case, as some nodes in the network
   * may be products of precursors of the target, but not themselves precursors.
   */
  public boolean isPrecursor(NetworkNode node) {
    return levelMap.get(node) != null;
  }

  /**
   * @return 0 for target node, 1 for its direct precursors, 2 for second-level precursors, etc.
   */
  public Integer getLevel(NetworkNode node) {
    return levelMap.get(node);
  }

  /**
   * Returns true if the substrate is exactly one level deeper in the precursor tree than the product.
   */
  public boolean edgeInBfsTree(NetworkNode substrate, NetworkNode product) {
    if (!isPrecursor(substrate) || !isPrecursor(product)) {
      return false;
    }
    return getLevel(substrate) == 1 + getLevel(product);
  }

  /**
   * Custom deserializer for Jackson, to transform the serialized String->Integer map back into a
   * NetworkNode->Integer map.
   */
  @JsonCreator
  private static PrecursorReport getFromJson(
      @JsonProperty("target") Metabolite target,
      @JsonProperty("network") MetabolismNetwork network,
      @JsonProperty("level_map") Map<String, Integer> levelMap) {
    PrecursorReport report = new PrecursorReport(target, network, new HashMap<>());
    network.getNodes().forEach(n -> report.levelMap.put(n, levelMap.get(n.getMetabolite().getInchi())));
    return report;
  }

  /**
   * Custom serializer for jackson, since it chokes on keys which are not Strings. Transforms levelMap to a
   * String->Integer map instead of a NetworkNode->Integer map.
   */
  @JsonProperty("level_map")
  private Map<String, Integer> getSerializableLevelMap() {
    return levelMap.entrySet().stream().collect(Collectors.toMap(
        e -> e.getKey().getMetabolite().getInchi(), e -> e.getValue()));
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public static PrecursorReport readFromJsonFile(File inputFile) throws IOException {
    return OBJECT_MAPPER.readValue(inputFile, PrecursorReport.class);
  }
}
