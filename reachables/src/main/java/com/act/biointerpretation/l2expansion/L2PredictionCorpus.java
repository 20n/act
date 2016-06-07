package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/*
 * Represents the set of all reaction predictions made by an L2 expansion run
 */
public class L2PredictionCorpus {
  @JsonProperty("corpus")
  List<L2Prediction> corpus;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  public void writePredictionsToJson(String outputFilePath) throws IOException {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    OBJECT_MAPPER.writeValue(getPredictionWriter(outputFilePath), this);
  }

  private BufferedWriter getPredictionWriter(String outputFilePath) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFilePath));
    return predictionWriter;
  }
}
