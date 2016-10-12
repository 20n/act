package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runnable class to build a metabolic network from a set of prediction corpuses.
 * For maximum flexibility
 */
public class NetworkBuilder implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkBuilder.class);

  private static final boolean FAIL_ON_INVALID_INPUT = false;

  private final List<File> corpusFiles;

  private final List<File> reactionIdFiles;
  private final MongoDB db;

  private final File outputFile;
  // True if the builder should read in every valid input file even if some inputs are invalid.
  // False if builder should crash on even a single invalid input file.
  private final boolean skipInvalidInputs;

  public NetworkBuilder(List<File> corpusFiles, List<File> reactionIdFile, MongoDB db, File outputFile) {
    this(corpusFiles, reactionIdFile, db, outputFile, FAIL_ON_INVALID_INPUT);
  }

  public NetworkBuilder(
      List<File> corpusFiles, List<File> reactionIdFiles, MongoDB db, File outputFile, boolean skipInvalidInputs) {
    this.corpusFiles = corpusFiles;
    this.reactionIdFiles = reactionIdFiles;
    this.db = db;
    this.outputFile = outputFile;
    this.skipInvalidInputs = skipInvalidInputs;
  }

  @Override
  public void run() throws IOException {
    LOGGER.info("Starting NetworkBuilder run.");

    // Check input files for validity
    for (File file : corpusFiles) {
      FileChecker.verifyInputFile(file);
    }
    for (File file : reactionIdFiles) {
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

    List<Integer> reactionIds = new ArrayList<>();
    for (File file : reactionIdFiles) {
      try {
        reactionIds.addAll(getReactionIdsFromFile(file));
      } catch (IOException e) {
        LOGGER.warn("Couldn't read file of name %s as list of reaction IDs.", file.getName());
        if (!skipInvalidInputs) {
          throw new IOException("Couldn't read reaction ID file " + file.getName() + ": " + e.getMessage());
        }
      }
    }
    LOGGER.info("Successfully read in input corpus files and reaction id files. Loading edges.");

    // Set up network object, and load predictions and reactions into network edges.
    MetabolismNetwork network = new MetabolismNetwork();
    corpuses.forEach(corpus -> network.loadPredictions(corpus));
    reactionIds.forEach(reactionId -> network.loadEdgeFromReaction(db, reactionId));
    LOGGER.info("Loaded predictions and reactions. Writing network to file.");

    // Write network out
    network.writeToJsonFile(outputFile);
    LOGGER.info("Complete! Network has been written to %s", outputFile.getAbsolutePath());
  }

  private List<Integer> getReactionIdsFromFile(File file) throws IOException {
    String rxnId;
    List<Integer> results = new ArrayList<>();

    BufferedReader reader = new BufferedReader(new FileReader(file));
    while ((rxnId = reader.readLine()) != null) {
      results.add(Integer.parseInt(rxnId.trim()));
    }

    return results;
  }
}
