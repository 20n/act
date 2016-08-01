package com.act.biointerpretation.sars.sartrees;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ScoredPredictionCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  Map<L2Prediction, Double> predictionScoreMap;

  public ScoredPredictionCorpus() {
    predictionScoreMap = new HashMap<L2Prediction, Double>();
  }


  public ScoredPredictionCorpus(Map<L2Prediction, Double> predictionScoreMap) {
    this.predictionScoreMap = predictionScoreMap;
  }


  public void loadFromFile(File file) throws IOException {
    ScoredPredictionCorpus fromFile = OBJECT_MAPPER.readValue(file, ScoredPredictionCorpus.class);
    this.setPredictionScoreMap(fromFile.getPredictionScoreMap());
  }

  public void writeToFile(File file) throws IOException {
    OBJECT_MAPPER.writeValue(file, this);
  }

  public Map<L2Prediction,Double> getPredictionScoreMap() {
    return predictionScoreMap;
  }

  public void setPredictionScoreMap(Map<L2Prediction, Double> predictionScoreMap) {
    this.predictionScoreMap = predictionScoreMap;
  }
}
