package com.act.biointerpretation.sars.sartrees;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScoredSarCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("sar_score_map")
  Map<SarTreeNode, Double> sarScoreMap;

  public ScoredSarCorpus() {
    sarScoreMap = new HashMap<SarTreeNode, Double>();
  }

  public ScoredSarCorpus(Map<SarTreeNode, Double> sarScoreMap) {
    this.sarScoreMap = sarScoreMap;
  }


  public void loadFromFile(File file) throws IOException {
    ScoredSarCorpus fromFile = OBJECT_MAPPER.readValue(file, ScoredSarCorpus.class);
    this.setSarScoreMap(fromFile.getSarScoreMap());
  }

  public void writeToFile(File file) throws IOException {
    OBJECT_MAPPER.writeValue(file, this);
  }

  public Map<SarTreeNode,Double> getSarScoreMap() {
    return sarScoreMap;
  }

  public void setSarScoreMap(Map<SarTreeNode, Double> sarScoreMap) {
    this.sarScoreMap = sarScoreMap;
  }
}
