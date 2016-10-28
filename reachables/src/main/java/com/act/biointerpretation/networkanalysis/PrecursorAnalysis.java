package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import com.act.lcms.v2.PeakSpectrum;
import com.act.utils.TSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Workflow component to take in a graph, calculate a precursor subgraph, and write the subgraph to file.
 */
public class PrecursorAnalysis implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PrecursorAnalysis.class);

  private static final String TARGET_ID_HEADER = "target_id";
  private static final String INCHI_HEADER = "InChI";
  private static final Double MASS_TOLERANCE = 0.01;

  private final File networkInput;
  private final Optional<File> lcmsInput;

  // The targets of the analysis: InChI strings of molecules whose network precursors we would like to identify.
  private final List<String> targets;
  private final File outputDirectory;
  // The number of edges/hops/steps to search backwards from the targets to identify relevant precursors in the network.
  private final int numSteps;

  private Set<String> ionSet = new HashSet<String>() {{
    add("M+H");
    add("M+Na");
    add("M+H-H2O");
  }};

  public PrecursorAnalysis(File networkInput, List<String> targets, int numSteps, File outputDirectory) {
    this(networkInput, Optional.empty(), targets, numSteps, outputDirectory);
  }

  public PrecursorAnalysis(File networkInput,
                           Optional<File> lcmsInput,
                           List<String> targets,
                           int numSteps,
                           File outputDirectory) {
    this.networkInput = networkInput;
    this.lcmsInput = lcmsInput;
    this.targets = targets;
    this.numSteps = numSteps;
    this.outputDirectory = outputDirectory;
  }

  public void setIons(Set<String> ions) {
    this.ionSet = ions;
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
    MetabolismNetwork network = MetabolismNetwork.getNetworkFromJsonFile(networkInput);
    LOGGER.info("Loaded network from file. Running precursor analyses.");

    Map<String, Integer> targetIdMap = new HashMap<>();
    int id = 0;
    MetaboliteToMz metaboliteToMz = lcmsInput.isPresent() ? new MetaboliteToMz(ionSet) : null;
    PeakSpectrum lcmsSpectrum = lcmsInput.isPresent() ? LcmsTSVParser.parseTSV(lcmsInput.get()) : null;

    // Do precursor analyses on each target.  Give each found target an ID so we can track which report is which.
    for (String target : targets) {
      Optional<NetworkNode> targetNode = network.getNodeOptionByInchi(target);
      if (targetNode.isPresent()) {
        PrecursorReport report = network.getPrecursorReport(targetNode.get(), numSteps);
        lcmsInput.ifPresent(a -> report.addLcmsData(lcmsSpectrum, metaboliteToMz, MASS_TOLERANCE));
        File outputFile = new File(outputDirectory, "precursors_target_" + id);
        report.writeToJsonFile(outputFile);
        LOGGER.info("Wrote target %s report to file %s", target, outputFile.getAbsolutePath());
        targetIdMap.put(target, id);
        id++;
      } else {
        LOGGER.warn("Target node %s not found in network!", target);
      }
    }

    // Write out the target IDs to file for reference.
    writeTargetIdMapToFile(targetIdMap, targetIdFile);
    LOGGER.info("Complete! Output files live in directory %s", outputDirectory.getAbsolutePath());
  }

  /**
   * Write out the target ID map. This is a TSV file where each line contains the ID followed by the target's InChI.
   *
   * @param targetIdMap The map to write.
   * @param targetIdFile The file to write to.
   * @throws IOException
   */
  private void writeTargetIdMapToFile(Map<String, Integer> targetIdMap, File targetIdFile) throws IOException {
    try (TSVWriter<String, String> writer = new TSVWriter<>(Arrays.asList(TARGET_ID_HEADER, INCHI_HEADER))) {
      writer.open(targetIdFile);
      for (Map.Entry<String, Integer> entry : targetIdMap.entrySet()) {
        writer.append(new HashMap<String, String>() {{
          put(TARGET_ID_HEADER, entry.getValue().toString());
          put(INCHI_HEADER, entry.getKey());
        }});
      }
    }
  }
}


