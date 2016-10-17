package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Runnable class to print out statistics about a metabolic network.
 * Very basic so far, but will be expanded as we are interested in more data.
 */
public class NetworkStats implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkStats.class);

  private final File inputFile;

  public NetworkStats(File inputFile) {
    this.inputFile = inputFile;
  }

  @Override
  public void run() throws IOException {
    // Check input file
    FileChecker.verifyInputFile(inputFile);
    LOGGER.info("Verified input file validity.");

    // Read in network from file
    MetabolismNetwork network = new MetabolismNetwork();
    network.loadFromJsonFile(inputFile);
    LOGGER.info("Loaded network in from file.");

    // Print stats on network
    LOGGER.info("Total nodes: %d", network.getNodes().size());
    LOGGER.info("Total edges: %d", network.getEdges().size());

    Set<String> orgs = new HashSet<String>();
    network.getEdges().forEach(e -> orgs.addAll(e.getOrgs()));
    LOGGER.info("Total organisms represented: %d", orgs.size());

    LOGGER.info("Complete!");
  }
}
