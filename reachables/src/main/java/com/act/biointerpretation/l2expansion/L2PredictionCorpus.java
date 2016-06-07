package com.act.biointerpretation.l2expansion;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class L2PredictionCorpus {
  @JsonProperty("corpus")
  List<L2Prediction> corpus;

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  public void printCorpus(String outFileName) throws IOException {
    BufferedWriter writer = getPredictionWriter(outFileName);

    System.out.println("Printing " + corpus.size() + " predictions to file.");

    for(L2Prediction prediction: corpus){
      prediction.printPrediction(writer);
      writer.write("\n");
    }

    writer.close();
  }

  /**
   * @return reader for the list of metabolites
   */
  public BufferedWriter getPredictionWriter(String outFileName) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outFileName));
    return predictionWriter;
  }

}
