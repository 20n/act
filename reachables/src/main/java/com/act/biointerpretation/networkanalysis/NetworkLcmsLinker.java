package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Reads in a network and an LCMS results object, and annotates the nodes of the network with their LCMS results.
 */
public class NetworkLcmsLinker implements JavaRunnable {

  private static final Logger LOGGER = LogManager.getFormatterLogger(NetworkLcmsLinker.class);

  private final File networkFile;
  private final File lcmsResultsFile;
  private final File outputFile;

  public NetworkLcmsLinker(File networkFile, File lcmsResultsFile, File outputFile) {
    this.lcmsResultsFile = lcmsResultsFile;
    this.networkFile = networkFile;
    this.outputFile = outputFile;
  }

  @Override
  public void run() throws IOException {
    // Verify input files
    FileChecker.verifyInputFile(lcmsResultsFile);
    FileChecker.verifyInputFile(networkFile);

    // Read in structures from files
    IonAnalysisInterchangeModel lcmsResults = new IonAnalysisInterchangeModel();
    lcmsResults.loadResultsFromFile(lcmsResultsFile);
    MetabolismNetwork network = new MetabolismNetwork();
    network.loadFromJsonFile(networkFile);

    // Add LCMS results to network
    network.getNodes().forEach(node -> node.setLcmsResult(lcmsResults.isMoleculeAHit(node.getInchi())));

    // Write out final network with LCMS data to file
    network.writeToJsonFile(outputFile);
  }
}
