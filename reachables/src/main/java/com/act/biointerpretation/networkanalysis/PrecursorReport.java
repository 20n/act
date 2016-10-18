package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to group all the data relating to a single precursor report
 */
public class PrecursorReport {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("target")
  Metabolite target;

  @JsonProperty("network")
  MetabolismNetwork precursors;

  @JsonProperty("lcms_results")
  Map<Metabolite, Boolean> lcmsResults;

  public PrecursorReport() {
    this(null, null, new HashMap<>());
  }

  public PrecursorReport(Metabolite target, MetabolismNetwork precursors) {
    this(target, precursors, new HashMap<>());
  }

  @JsonCreator
  public PrecursorReport(
      @JsonProperty("target") Metabolite target,
      @JsonProperty("network") MetabolismNetwork precursors,
      @JsonProperty("lcms_results") Map<Metabolite, Boolean> lcmsResults) {
    this.target = target;
    this.precursors = precursors;
    this.lcmsResults = lcmsResults;
  }

  public void populateLcmsResults() {

  }

  public Metabolite getTarget() {
    return target;
  }

  public MetabolismNetwork getNetwork() {
    return precursors;
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public void loadFromJsonFile(File inputFile) throws IOException {
    PrecursorReport template = OBJECT_MAPPER.readValue(inputFile, PrecursorReport.class);
    this.target = template.target;
    this.precursors = template.precursors;
    this.lcmsResults = template.lcmsResults;
  }
}
