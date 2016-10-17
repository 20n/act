package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow component to take in a graph,calculate a precursor subgraph, and write the subgraph to file.
 */
public class PrecursorAnalysis implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkBuilder.class);

  private final File networkInput;
  private final List<String> targets;
  private final File outputDirectory;
  private final int numSteps;

  public PrecursorAnalysis(File networkInput, List<String> targets, int numSteps, File outputDirectory) {
    this.networkInput = networkInput;
    this.targets = targets;
    this.numSteps = numSteps;
    this.outputDirectory = outputDirectory;
  }

  @Override
  public void run() throws IOException {
    File targetIdFile = new File(outputDirectory, "targetIds");

    // Verify files
    FileChecker.verifyInputFile(networkInput);
    FileChecker.verifyOrCreateDirectory(outputDirectory);
    FileChecker.verifyAndCreateOutputFile(targetIdFile);
    LOGGER.info("Verified files. Loading network");

    // Get input network
    MetabolismNetwork network = new MetabolismNetwork();
    network.loadFromJsonFile(networkInput);
    LOGGER.info("Loaded network from file. Running precursor analyses.");

    Map<String, Integer> targetIdMap = new HashMap<>();
    int id = 0;

    // Do precursor analyses
    for (String target : targets) {
      Optional<NetworkNode> targetNode = network.getNodeOption(target);
      if (targetNode.isPresent()) {
        MetabolismNetwork precursorNetwork = network.getPrecursorSubgraph(targetNode.get(), numSteps);
        File outputFile = new File(outputDirectory, "precursors_target_" + id);
        precursorNetwork.writeToJsonFile(outputFile);
        targetIdMap.put(target, id);
        id++;
      } else {
        LOGGER.warn("Target node %s not found in network!", target);
      }
    }

    writeTargetIdMapToFile(targetIdMap, targetIdFile);
    LOGGER.info("Complete! Output files live in directory %s", outputDirectory.getAbsolutePath());
  }

  private void writeTargetIdMapToFile(Map<String, Integer> targetIdMap, File targetIdFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetIdFile))) {
      writer.write("TARGET_ID\tINCHI");
      for (Map.Entry entry : targetIdMap.entrySet()) {
        writer.newLine();
        writer.write(entry.getValue().toString() + "\t" + entry.getKey().toString());
      }
    }
  }
}

