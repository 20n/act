package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the set of all reaction predictions made by an L2 expansion run
 */
public class L2PredictionCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("corpus")
  List<L2Prediction> corpus;

  public L2PredictionCorpus() {
    this.corpus = new ArrayList<L2Prediction>();
  }

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  /**
   * Filters the corpus by removing the predictions that don't pass the filter.
   * @param filter The filter to apply to this corpus.
   */
  public void applyFilter(PredictionFilter filter){
    corpus.removeIf(s -> !filter.test(s));
  }

  /**
   * Write the L2PredictionCorpus to file in json format.
   * @param outputFilePath Where to write the file.
   * @throws IOException
   */
  public void writePredictionsToJsonFile(String outputFilePath) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFilePath));
    OBJECT_MAPPER.writeValue(predictionWriter, this);
  }

  public void addPrediction(L2Prediction prediction){
    corpus.add(prediction);
  }
}
