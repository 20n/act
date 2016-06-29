package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents the set of all predictions made by an L2 expansion run
 */
public class L2PredictionCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("corpus")
  private List<L2Prediction> corpus;

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
   * Read a prediction corpus from file.
   *
   * @param corpusFile The file to read.
   * @return The L2PredictionCorpus.
   * @throws IOException
   */
  public static L2PredictionCorpus readPredictionsFromJsonFile(File corpusFile) throws IOException {
    return OBJECT_MAPPER.readValue(corpusFile, L2PredictionCorpus.class);
  }

  /**
   * Applies a transformation to this L2PredictionCorpus, which acts on each prediction in the corpus.
   * Returns a new corpus with the results; this corpus is not modified.
   *
   * @param transformation the transformation to apply..
   */
  public L2PredictionCorpus applyTransformation(Function<L2Prediction, L2Prediction> transformation) throws IOException {
    L2PredictionCorpus newCorpus = new L2PredictionCorpus();

    for (L2Prediction prediction : getCorpus()) {
      newCorpus.addPrediction(transformation.apply(prediction.getDeepCopy()));
    }

    return newCorpus;
  }

  /**
   * Applies a filter to this L2PredictionCorpus, returning a new corpus with only those predictions that pass
   * the filter. This corpus is not modified, and the predictions in the new corpus are deep copies of the
   * predictions in the original corpus.
   *
   * @param filter The filter to be used.
   */
  public L2PredictionCorpus applyFilter(Predicate<L2Prediction> filter) throws IOException {
    L2PredictionCorpus newCorpus = new L2PredictionCorpus();

    for (L2Prediction prediction : getCorpus()) {
      L2Prediction predictionCopy = prediction.getDeepCopy();
      if (filter.test(predictionCopy)) {
        newCorpus.addPrediction(predictionCopy);
      }
    }

    return newCorpus;
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

  /**
   * Write the inchis of the products produced by this corpus to a file, with one inchi per line.
   *
   * @param outputFile The file to which to print the results.
   * @throws IOException
   */
  public void writeProductInchiFile(File outputFile) throws IOException {
    Set<String> inchiSet = new HashSet<>();

    for (L2Prediction prediction : getCorpus()) {
      inchiSet.addAll(prediction.getProductInchis());
    }

    try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(outputFile))) {
      for (String inchi : inchiSet) {
        fileWriter.write(inchi);
        fileWriter.newLine();
      }
    }
  }

  public void addPrediction(L2Prediction prediction) {
    corpus.add(prediction);
  }

  /**
   * Returns the count of the predictions matching some given predicate.
   *
   * @param predicate The predicate.
   * @return The number of matching predictions.
   */
  public int countPredictions(Predicate<L2Prediction> predicate) {
    int count = 0;
    for (L2Prediction prediction : corpus) {
      if (predicate.test(prediction)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets a list of distinct ROs seen in this prediction corpus.
   *
   * @return The list of ROs.
   */
  @JsonIgnore
  public List<L2PredictionRo> getAllRos() {
    Set<Integer> rosSeen = new HashSet();
    List<L2PredictionRo> result = new ArrayList<>();

    for (L2Prediction prediction : getCorpus()) {
      if (!rosSeen.contains(prediction.getRo().getId())) {
        result.add(prediction.getRo());
        rosSeen.add(prediction.getRo().getId());
      }
    }

    return result;
  }
}
