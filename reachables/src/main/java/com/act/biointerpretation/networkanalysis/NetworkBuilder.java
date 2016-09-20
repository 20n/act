package com.act.biointerpretation.networkanalysis;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NetworkBuilder implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkBuilder.class);

  private final List<File> corpusFiles;
  private final File outputFile;

  public NetworkBuilder(List<File> corpusFiles, File outputFile) {
    this.corpusFiles = corpusFiles;
    this.outputFile = outputFile;
  }

  @Override
  public void run() throws IOException {
    LOGGER.info("Starting NetworkBuilder run.");

    // Check input files for validity
    for (File file : corpusFiles) {
      FileChecker.verifyInputFile(file);
    }
    FileChecker.verifyAndCreateOutputFile(outputFile);

    // Read in input corpuses
    int successCount = 0;
    List<L2PredictionCorpus> corpuses = new ArrayList<>(corpusFiles.size());
    for (File file : corpusFiles) {
      try {
        corpuses.add(L2PredictionCorpus.readPredictionsFromJsonFile(file));
        successCount++;
      } catch (IOException e) {
        LOGGER.warn("Couldn't read file of name %s as input corpus", file.getName());
      }
    }
    LOGGER.info("Read in %d input files. Loading corpuses.", successCount);

    // Set up network object, load from corpuses
    Network network = new Network();
    corpuses.forEach(corpus -> network.loadSingleSubstratePredictions(corpus));
    LOGGER.info("Loaded corpuses. Writing and reading in files");

    // Write network out
    network.writeToJsonFile(outputFile);

    // Read network back in
    network.loadFromJsonFile(outputFile);
    LOGGER.info("Complete!");

  }
}
