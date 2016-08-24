package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents the set of all predictions made by an L2 expansion run
 */
public class L2PredictionCorpus implements Serializable {
  private static final long serialVersionUID = 2502953593841339815L;

  /* TODO: add tests of serialization for this class an its neighbors.  We should ensure we can successfully consume
   * prediction results w/ any class of SAR so we don't get stuck with results that are locked up in unreadable JSON. */
  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("corpus")
  private List<L2Prediction> corpus;

  @JsonIgnore
  private Map<Integer, L2Prediction> idToPredictionMap;

  public L2PredictionCorpus() {
    this(new ArrayList<>());
  }

  public L2PredictionCorpus(List<L2Prediction> corpus) {
    this.corpus = corpus;
    populateIdToPredictionMap();
  }

  public List<L2Prediction> getCorpus() {
    return corpus;
  }

  /**
   * Read a prediction corpus from file, and populate its prediction map.
   *
   * @param corpusFile The file to read.
   * @return The L2PredictionCorpus.
   * @throws IOException
   */
  public static L2PredictionCorpus readPredictionsFromJsonFile(File corpusFile) throws IOException {
    return L2PredictionCorpus.OBJECT_MAPPER.readValue(corpusFile, L2PredictionCorpus.class).populateIdToPredictionMap();
  }

  /**
   * Gets the prediction with the given ID from the prediction corpus.
   *
   * @param id The prediction ID to find.
   * @return The corresponding prediction.
   * @throws IllegalArgumentException if the id is not present in the prediction map.
   */
  @JsonIgnore
  public L2Prediction getPredictionFromId(Integer id) {
    L2Prediction result = idToPredictionMap.get(id);
    if (result != null) {
      return result;
    }
    throw new IllegalArgumentException("Id " + id + " is not present in corpus, or the id->prediction map has not " +
            "been repopulated since it was added.");
  }

  /**
   * Add all prediction IDs to idToPredictionMap. This is called on construction and load from file, but should be
   * re-called after predictions are added to the corpus, if getPredictionFromId is to be used.
   */
  public L2PredictionCorpus populateIdToPredictionMap() {
    this.idToPredictionMap = new HashMap<>();
    corpus.forEach(prediction -> idToPredictionMap.put(prediction.getId(), prediction));
    return this;
  }

  /**
   * Applies a transformation to this L2PredictionCorpus, which acts on each prediction in the corpus.
   * Returns a new corpus with the results; this corpus is not modified.
   *
   * @param transformation The transformation to apply.
   * @return The transformed corpus.
   */
  public L2PredictionCorpus applyTransformation(Function<L2Prediction, L2Prediction> transformation) throws IOException {
    L2PredictionCorpus newCorpus = new L2PredictionCorpus();

    for (L2Prediction prediction : getCorpus()) {
      newCorpus.addPrediction(transformation.apply(new L2Prediction(prediction)));
    }

    return newCorpus;
  }

  /**
   * Applies a filter to this L2PredictionCorpus, returning a new corpus with only those predictions that pass
   * the filter. This corpus is not modified, and the predictions in the new corpus are deep copies of the
   * predictions in the original corpus.
   *
   * @param filter The filter to be used.
   * @return The filtered corpus.
   */
  public L2PredictionCorpus applyFilter(Predicate<L2Prediction> filter) throws IOException {
    L2PredictionCorpus newCorpus = new L2PredictionCorpus();

    for (L2Prediction prediction : getCorpus()) {
      L2Prediction predictionCopy = new L2Prediction(prediction);
      if (filter.test(predictionCopy)) {
        newCorpus.addPrediction(predictionCopy);
      }
    }

    return newCorpus;
  }

  /**
   * Applies a function to each prediction in the corpus, and splits the corpus into one corpus for each distinct
   * output value of that function.  For example, this could be used to split a corpus into one corpus per distinct
   * projector used to build it.
   *
   * @param classifier The function to apply to each element.
   * @return A map from values produced by the classifier, to the corresponding PredictionCorpus.
   */
  public <T> Map<T, L2PredictionCorpus> splitCorpus(Function<L2Prediction, T> classifier) throws IOException {
    Map<T, L2PredictionCorpus> corpusMap = new HashMap<>();

    for (L2Prediction prediction : getCorpus()) {
      L2Prediction predictionCopy = new L2Prediction(prediction);
      T key = classifier.apply(predictionCopy);
      L2PredictionCorpus corpus = corpusMap.get(key);
      if (corpus == null) {
        corpus = new L2PredictionCorpus();
        corpusMap.put(key, corpus);
      }
      corpus.addPrediction(predictionCopy);
    }

    return corpusMap;
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
   * Write the L2PredictionCorpus to file in list of inchis format.
   *
   * @param outputFile Where to write the file.
   * @throws IOException
   */
  public void writePredictionsAsInchiList(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      Set<String> productInchis = this.getUniqueProductInchis();
      for (String inchi : productInchis) {
        predictionWriter.write(inchi);
        predictionWriter.newLine();
      }
    }
  }

  /**
   * Get a list of all product inchis from corpus.
   */
  @JsonIgnore
  public Set<String> getUniqueProductInchis() {
    Set<String> inchiSet = new HashSet<>();
    for (L2Prediction prediction : getCorpus()) {
      inchiSet.addAll(prediction.getProductInchis());
    }
    return inchiSet;
  }

  /**
   * Get a list of all substrate inchis from corpus.
   */
  @JsonIgnore
  public Set<String> getUniqueSubstrateInchis() {
    Set<String> inchiSet = new HashSet<>();
    for (L2Prediction prediction : getCorpus()) {
      inchiSet.addAll(prediction.getSubstrateInchis());
    }
    return inchiSet;
  }

  public void addPrediction(L2Prediction prediction) {
    corpus.add(prediction);
  }

  public void addAll(Collection<L2Prediction> predictions) {
    for (L2Prediction prediction : predictions) {
      addPrediction(prediction);
    }
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
}