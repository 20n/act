package com.act.biointerpretation.l2expansion;


import com.fasterxml.jackson.annotation.JsonProperty;

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

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  /**
   * @return writer for the list of predictions
   */
  public BufferedWriter getPredictionWriter(String outFileName) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outFileName));
    return predictionWriter;
  }

}
