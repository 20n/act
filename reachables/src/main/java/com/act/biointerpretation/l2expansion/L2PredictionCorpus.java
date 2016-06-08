package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
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
}
