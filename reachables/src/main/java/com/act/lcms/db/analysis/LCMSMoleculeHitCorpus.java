package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LCMSMoleculeHitCorpus {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("corpus")
  private List<L2Prediction> corpus;

  public LCMSMoleculeHitCorpus() {
    this.corpus = new ArrayList<L2Prediction>();
  }

  public LCMSMoleculeHitCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  /**
   * Read a prediction corpus from file.
   *
   * @param corpusFile The file to read.
   * @return The L2PredictionCorpus.
   * @throws IOException
   */
  public static LCMSMoleculeHitCorpus readPredictionsFromJsonFile(File corpusFile) throws IOException {
    return OBJECT_MAPPER.readValue(corpusFile, LCMSMoleculeHitCorpus.class);
  }

  /**
   * Write the L2PredictionCorpus to file in json format.
   *
   * @param outputFile Where to write the file.
   * @throws IOException
   */
  public void writePredictionsToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public void addPrediction(L2Prediction prediction) {
    corpus.add(prediction);
  }

  public void addAll(Collection<L2Prediction> predictions) {
    for (L2Prediction prediction : predictions) {
      addPrediction(prediction);
    }
  }
}
