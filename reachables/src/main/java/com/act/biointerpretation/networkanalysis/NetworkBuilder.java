package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Runnable class to build a metabolic network from a set of prediction corpuses.
 * For maximum flexibility
 */
public class NetworkBuilder implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkBuilder.class);

  private final Optional<File> seedNetwork;

  private final List<File> corpusFiles;
  private final Optional<MongoDB> db;

  private final File outputFile;
  // True if the builder should read in every valid input file even if some inputs are invalid.
  // False if builder should crash on even a single invalid input file.
  private final boolean skipInvalidInputs;

  public NetworkBuilder(
      Optional<File> seedNetwork, List<File> corpusFiles, MongoDB db, File outputFile, boolean skipInvalidInputs) {
    this.seedNetwork = seedNetwork;
    this.corpusFiles = corpusFiles;
    this.db = Optional.ofNullable(db);
    this.outputFile = outputFile;
    this.skipInvalidInputs = skipInvalidInputs;
  }

  @Override
  public void run() throws IOException {
    LOGGER.info("Starting NetworkBuilder run.");

    // Check input files for validity
    if (seedNetwork.isPresent()) {
      FileChecker.verifyInputFile(seedNetwork.get());
    }
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
        LOGGER.warn("Couldn't read file of name %s as input corpus.", file.getName());
        if (!skipInvalidInputs) {
          throw new IOException("Couldn't read input corpus file " + file.getName() + ": " + e.getMessage());
        }
      }
    }

    // Set up network object, and load predictions and reactions into network edges.
    MetabolismNetwork network;
    if (seedNetwork.isPresent()) {
      network = MetabolismNetwork.getNetworkFromJsonFile(seedNetwork.get());
    } else {
      network = new MetabolismNetwork();
    }
    LOGGER.info("Created starting network! Loading edges from DB.");

    db.ifPresent(network::loadAllEdgesFromDb);
    LOGGER.info("Done loading edges from DB, if any. Loading from corpuses.");

    corpuses.forEach(corpus -> network.loadPredictions(corpus));
    LOGGER.info("Done loading predictions from input corpuses. Writing network to file.");

    // Write network out
    network.writeToJsonFile(outputFile);
    LOGGER.info("Complete! Network has been written to %s", outputFile.getAbsolutePath());
  }
}
