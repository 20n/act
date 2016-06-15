package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Represents the set of all predictions made by an L2 expansion run
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
   * Applies a PredictionFilter to this L2PredictionCorpus. Applies the filter to each prediction in the corpus,
   * and concatenates the resulting lists of new predictions.
   * This may modify the original predictions in the Corpus.  In general the workflow should be applying filters
   * to transform a corpus, and saving to file whenever you want a snapshot saved.  Don't rely on a corpus not
   * mutating as you apply more filters.
   *
   * @param filter The filter to be used.
   */
  public void applyFilter(Function<L2Prediction, List<L2Prediction>> filter) {

    List<L2Prediction> newCorpus = new ArrayList<L2Prediction>();

    for (L2Prediction prediction : corpus) {
      for (L2Prediction newPrediction : filter.apply(prediction)) {
        newCorpus.add(newPrediction);
      }
    }

    this.corpus = newCorpus;
  }

  /**
   * Write the L2PredictionCorpus to file in json format.
   *
   * @param outputFilePath Where to write the file.
   * @throws IOException
   */
  public void writePredictionsToJsonFile(String outputFilePath) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFilePath));
    OBJECT_MAPPER.writeValue(predictionWriter, this);
  }

  public void addPrediction(L2Prediction prediction) {
    corpus.add(prediction);
  }
}
