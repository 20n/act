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

/**
 * Runnable class to build a metabolic network from a set of prediction corpuses.
 * For maximum flexibility
 */
public class NetworkBuilder implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkBuilder.class);

  private final List<File> corpusFiles; // Input files
  private final File outputFile; // The file to which the network structure will be written.
  // False if the builder should read in every valid input file even if some inputs are invalid.
  // True if builder should crash on even a single invalid input file.
  private boolean failOnInvalidInput = false;

  public NetworkBuilder(List<File> corpusFiles, File outputFile) {
    this.corpusFiles = corpusFiles;
    this.outputFile = outputFile;
  }

  public void setFailOnInvalidInput(boolean fail) {
    failOnInvalidInput = fail;
  }

  @Override
  public void run() throws IOException {
    LOGGER.info("Starting NetworkBuilder run.");

    // Check input files for validity
    for (File file : corpusFiles) {
      FileChecker.verifyInputFile(file);
    }
    FileChecker.verifyAndCreateOutputFile(outputFile);
    LOGGER.info("Checked input files for validity.");

    // Read in input corpuses
    List<L2PredictionCorpus> corpuses = new ArrayList<>(corpusFiles.size());
    for (File file : corpusFiles) {
      try {
        corpuses.add(L2PredictionCorpus.readPredictionsFromJsonFile(file));
      } catch (IOException e) {
        if (failOnInvalidInput) {
          throw new IOException("Couldn't read input file " + file.getName() + ": " + e.getMessage());
        }
        LOGGER.warn("Couldn't read file of name %s as input corpus; ignoring this file.", file.getName());
      }
    }
    LOGGER.info("Successfully read in %d input files. Loading edges into network.", corpuses.size());

    // Set up network object, and loading predictions from corpuses into network edges.
    Network network = new Network();
    corpuses.forEach(corpus -> network.loadSingleSubstratePredictions(corpus));
    LOGGER.info("Loaded corpuses. Writing network to file.");

    // Write network out
    network.writeToJsonFile(outputFile);
    LOGGER.info("Complete!");
  }
}
