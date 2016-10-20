package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Group all the data relating to a single precursor report
 * TODO: add LCMS data to the report
 */
public class PrecursorReport {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("target")
  Metabolite target;

  @JsonProperty("network")
  MetabolismNetwork precursors;

  @JsonCreator
  public PrecursorReport(
      @JsonProperty("target") Metabolite target,
      @JsonProperty("network") MetabolismNetwork precursors) {
    this.target = target;
    this.precursors = precursors;
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

  public static PrecursorReport readFromJsonFile(File inputFile) throws IOException {
    return OBJECT_MAPPER.readValue(inputFile, PrecursorReport.class);
  }
}
