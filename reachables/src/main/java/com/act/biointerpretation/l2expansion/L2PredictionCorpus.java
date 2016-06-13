package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Predicate;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents the set of all predictions made by an L2 expansion run
 */
public class L2PredictionCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
          withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true);

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
   * Read an L2PredictionCorpus from file in json format.
   *
   * @param inputFilePath Where to read the file from.
   * @throws IOException
   */
  public static L2PredictionCorpus readPredictionsFromJsonFile(String inputFilePath) throws IOException {
    File corpusFile = new File(inputFilePath);
    return OBJECT_MAPPER.readValue(corpusFile, L2PredictionCorpus.class);
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
  public void applyFilter(Function<L2Prediction, Optional<L2Prediction>> filter) {

    List<L2Prediction> newCorpus = new ArrayList<L2Prediction>();

    for (L2Prediction prediction : corpus) {
      Optional<L2Prediction> result = filter.apply(prediction);
      if (result.isPresent()) {
        newCorpus.add(result.get());
      }
    }

    this.corpus = newCorpus;
  }

  /**
   * Write the L2PredictionCorpus to file in json format.
   *
   * @param outputFile Where to write the file.
   * @throws IOException
   */
  public void writePredictionsToJsonFile(File outputFile) throws IOException {
    BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile));
    OBJECT_MAPPER.writeValue(predictionWriter, this);
  }

  /**
   * Write a TSV file to be used for further curation of the results of this corpus.
   * Format is four lines per prediction:
   * Line 1: Integer index of prediction in corpus
   * Line 2: [RO id] [RO string]
   * Line 3: [substrate 1 id] [substrate 1 inchi] ...
   * Line 4: [product 1 id] [product 1 inchi] [product 1 name] ...
   *
   * @param outputFilePath Where to write the file.
   * @throws IOException
   */
  public void writePredictionsToTSVFile(String outputFilePath) throws IOException {

    try (FileWriter tsvWriter = new FileWriter(outputFilePath);
         CSVPrinter csvPrinter = new CSVPrinter(tsvWriter, TSV_FORMAT)) {

      int i = 0;
      for (L2Prediction prediction : corpus) {
        csvPrinter.print(i);
        csvPrinter.println();
        for (List<Object> line : prediction.getTSVLines()) {
          csvPrinter.printRecord(line);
        }
        i++;
      }

      csvPrinter.flush();
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
      if (predicate.apply(prediction)) {
        count++;
      }
    }
    return count;
  }
}
