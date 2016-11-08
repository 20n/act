package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.PeakSpectrum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Group all the data relating to a single precursor report.  The report contains a target metabolite,
 * an ImmutableNetwork containing its precursors, and a Map associating each node to its level in the backwards
 * BF search tree.
 * TODO: add LCMS data to the report
 */
public class PrecursorReport {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("target")
  Metabolite target;

  @JsonProperty("network")
  ImmutableNetwork network;

  @JsonProperty("level_map")
  Map<NetworkNode, Integer> levelMap;

  // Maps nodes to confidence that they're in the lcms trace, from 0.0 to 1.0.
  @JsonProperty("lcms_hit_map")
  Map<NetworkNode, Double> lcmsMap;

  public PrecursorReport(Metabolite target, ImmutableNetwork network) {
    this(target, network, new HashMap<>(), new HashMap<>());
  }

  public PrecursorReport(
      Metabolite target,
      ImmutableNetwork network,
      Map<NetworkNode, Integer> levelMap) {
    this(target, network, levelMap, new HashMap<>());
  }

  public PrecursorReport(
      Metabolite target,
      ImmutableNetwork network,
      Map<NetworkNode, Integer> levelMap,
      Map<NetworkNode, Double> lcmsMap) {
    this.target = target;
    this.network = network;
    this.levelMap = levelMap;
    this.lcmsMap = lcmsMap;
  }

  public void addLcmsData(PeakSpectrum peakSpectrum, IonCalculator massCalculator, Set<String> ions, double massTol) {
    for (NetworkNode node : network.getNodes()) {
      lcmsMap.put(node, 0.0);
      for (Ion ion : massCalculator.getSelectedIons(node.getMetabolite(), ions, MS1.IonMode.POS)) {
        if (!peakSpectrum.getPeaksByMZ(ion.getMzValue(), 1.0).isEmpty()) {
          lcmsMap.put(node, 1.0);
          break;
        }
      }
    }
  }

  public Metabolite getTarget() {
    return target;
  }

  @JsonProperty("network")
  public ImmutableNetwork getNetwork() {
    return network;
  }

  /**
   * Check if a given node is a precursor of the target node. This is not always the case, as some nodes in the network
   * may be products of precursors of the target, but not themselves precursors.
   */
  public boolean isPrecursor(NetworkNode node) {
    return levelMap.containsKey(node);
  }

  /**
   * @return 0 for target node, 1 for its direct precursors, 2 for second-level precursors, etc. Returns null if the
   * node is not in the level map, i.e. if it's not a precursor, and isPrecursor(node) == false.
   */
  public Integer getLevel(NetworkNode node) {
    return levelMap.get(node);
  }

  /**
   * Returns true if the node is an lcms hit, false if not.  If the node isn't in the map, returns null.
   */
  public Double getLcmsConfidence(NetworkNode node) {
    return lcmsMap.get(node);
  }

  /**
   * Returns true if the substrate is exactly one level deeper in the precursor tree than the product.
   */
  public boolean edgeInBfsTree(NetworkNode substrate, NetworkNode product) {
    return isPrecursor(substrate) && isPrecursor(product) && getLevel(substrate).equals(1 + getLevel(product));
  }

  /**
   * Custom deserializer for Jackson, to transform the serialized String->Integer map back into a
   * NetworkNode->Integer map.
   */
  @JsonCreator
  private static PrecursorReport getFromJson(
      @JsonProperty("target") InchiMetabolite target,
      @JsonProperty("network") ImmutableNetwork network,
      @JsonProperty("level_map") Map<Integer, Integer> levelMap,
      @JsonProperty("lcms_hit_map") Map<Integer, Double> lcmsMap) {
    PrecursorReport report = new PrecursorReport(target, network);
    levelMap.entrySet().forEach(e -> report.levelMap.put(network.getNodeByUID(e.getKey()), e.getValue()));
    lcmsMap.entrySet().forEach(e -> report.lcmsMap.put(network.getNodeByUID(e.getKey()), e.getValue()));
    ;
    return report;
  }

  /**
   * Custom serializer for jackson, since it chokes on keys which are not strings. Transforms lcmsMap to a
   * String->Boolean map instead of a NetworkNode->Integer map.
   */
  @JsonProperty("lcms_hit_map")
  private Map<Integer, Double> getSerializableLcmsMap() {
    return lcmsMap.entrySet().stream().collect(Collectors.toMap(
        e -> e.getKey().getUID(), e -> e.getValue()));
  }

  /**
   * Custom serializer for jackson, since it chokes on keys which are not Strings. Transforms levelMap to a
   * String->Integer map instead of a NetworkNode->Integer map.
   */
  @JsonProperty("level_map")
  private Map<Integer, Integer> getSerializableLevelMap() {
    return levelMap.entrySet().stream().collect(Collectors.toMap(
        e -> e.getKey().getUID(), e -> e.getValue()));
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
