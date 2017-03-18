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
    MetabolismNetwork network = MetabolismNetwork.getNetworkFromJsonFile(inputFile);

    // Print stats on network
    LOGGER.info("Total nodes: %d", network.getNodes().size());
    LOGGER.info("Total edges: %d", network.getEdges().size());

    Set<String> orgs = new HashSet<String>();
    network.getEdges().forEach(e -> orgs.addAll(e.getOrgs()));
    LOGGER.info("Total organisms represented: %d", orgs.size());

    LOGGER.info("Complete!");
  }
}
