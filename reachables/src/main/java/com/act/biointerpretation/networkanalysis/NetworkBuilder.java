package com.act.biointerpretation.networkanalysis;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NetworkBuilder implements JavaRunnable {

  private final List<File> corpusFiles;
  private final File outputFile;

  public NetworkBuilder(List<File> corpusFiles, File outputFile) {
    this.corpusFiles = corpusFiles;
    this.outputFile = outputFile;
  }

  @Override
  public void run() throws IOException {
    // Check input files for validity
    for (File file : corpusFiles) {
      FileChecker.verifyInputFile(file);
    }
    FileChecker.verifyAndCreateOutputFile(outputFile);

    // Read in input corpuses
    List<L2PredictionCorpus> corpuses = new ArrayList<>(corpusFiles.size());
    for (File file : corpusFiles) {
      corpuses.add(L2PredictionCorpus.readPredictionsFromJsonFile(file));
    }

    // Set up network object, load from corpuses
    Network network = new Network();
    corpuses.forEach(corpus -> network.loadSingleSubstratePredictions(corpus));

    // Write network out
    network.writeToJsonFile(outputFile);

    // Read network back in
    network.loadFromJsonFile(outputFile);
  }
}
