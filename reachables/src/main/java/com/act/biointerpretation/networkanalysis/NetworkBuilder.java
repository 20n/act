/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a metabolic network from any of several sources. Can take edges from an existing network,
 * a database of reactions, or prediction corpuses.
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
      File seedNetwork, List<File> corpusFiles, Optional<MongoDB> db, File outputFile, boolean skipInvalidInputs) {
    this.seedNetwork = Optional.ofNullable(seedNetwork);
    this.corpusFiles = corpusFiles;
    this.db = db;
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
    LOGGER.info("Done loading edges from DB, if any. Loading edges from any supplied prediction corpuses.");

    corpuses.forEach(corpus -> network.loadPredictions(corpus));
    LOGGER.info("Done loading predictions from input corpuses. Writing network to file.");

    // Write network out
    network.writeToJsonFile(outputFile);
    LOGGER.info("Complete! Network has been written to %s", outputFile.getAbsolutePath());
  }
}
