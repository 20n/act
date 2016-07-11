package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.chemaxon.search.mcs.MaxCommonSubstructure;
import com.chemaxon.search.mcs.McsSearchOptions;
import com.chemaxon.search.mcs.RingHandlingMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class McsCalculator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(McsCalculator.class);

  private static final McsSearchOptions DEFAULT_OPTIONS =
      new McsSearchOptions.Builder()
          .bondTypeMatching(false)
          .ringHandlingMode(RingHandlingMode.KEEP_RINGS)
          .build();

  private final MaxCommonSubstructure mcs;

  public McsCalculator() {
    mcs = MaxCommonSubstructure.newInstance(DEFAULT_OPTIONS);
  }

  public McsCalculator(MaxCommonSubstructure mcs) {
    this.mcs = mcs;
  }

  /**
   * Gets MCS of any number of molecules by recursively applying Chemaxon's MCS search to all substrates.
   * For an array of n molecules, this will use between n/2 and n individual MCS operations.
   *
   * @param molecules The molecules to get the MCS of.
   * @return The MCS of all input molecules.
   */
  public Molecule getMCS(List<Molecule> molecules) {
    if (molecules.isEmpty()) {
      throw new IllegalArgumentException("Cannot get MCS of empty list of molecules.");
    }
    return getMcsHelper(molecules, 0, molecules.size());
  }

  /**
   * Helper method to implement hierachical MCS search.
   * Gets MCS by recursively getting MCS of each half of the list, and then combining the two halves with
   * a final MCS calculation.
   *
   * @param molecules The whole molecules array.
   * @param startIndex The index of the first molecule to consider for this recursive call.
   * @param partitionSize The number of molecules to consider in this recursive call.
   */
  private Molecule getMcsHelper(List<Molecule> molecules, int startIndex, int partitionSize) {
    // This case shouldn't happen if this method is used only through getMCS interface
    if (partitionSize <= 0) {
      LOGGER.error("Can't only getMCsHelper on a partition of positive size. %d", partitionSize);
    }

    // Handle base case of a single Molecule or pair of Molecules
    if (partitionSize == 1) {
      return molecules.get(startIndex);
    }
    if (partitionSize == 2) {
      return getMcsOfPair(molecules.get(startIndex), molecules.get(startIndex + 1));
    }

    // Recursively handle each half of list.
    int firstHalfSize = partitionSize / 2;
    int secondHalfStart = startIndex + firstHalfSize;
    Molecule leftMcs = getMcsHelper(molecules, startIndex, firstHalfSize);
    Molecule rightMcs = getMcsHelper(molecules, secondHalfStart, partitionSize - firstHalfSize);

    // Return MCS of the results of the recursion.
    return getMcsOfPair(leftMcs, rightMcs);
  }

  /**
   * Helper method to find MCS of exactly two molecules.
   */
  private Molecule getMcsOfPair(Molecule moleculeA, Molecule moleculeB) {
    mcs.setMolecules(moleculeA, moleculeB);
    return mcs.nextResult().getAsMolecule();
  }
}
