package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.chemaxon.search.mcs.MaxCommonSubstructure;
import com.chemaxon.search.mcs.McsSearchOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class McsCalculator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(McsCalculator.class);

  private static MaxCommonSubstructure MCS = MaxCommonSubstructure.newInstance();

  static {
    McsSearchOptions.Builder builder = new McsSearchOptions.Builder();
    builder.bondTypeMatching(false);
    MCS = MaxCommonSubstructure.newInstance(builder.build());
  }

  /**
   * Gets MCS of any number of molecules by hierarchically applying Chemaxon's MCS search to all substrates.
   *
   * @param molecules The molecules to get the MCS of.
   * @return The MCS of all input molecules.
   */
  public Molecule getMCS(List<Molecule> molecules) {
    return getMcsHelper(molecules, 0, molecules.size());
  }

  /**
   * Helper method to implement hierachical MCS search.
   */
  private Molecule getMcsHelper(List<Molecule> molecules, int start, int end) {
    if (end <= start) {
      LOGGER.error("Can't call getMCsHelper with end <= start: %d, %d", start, end);
    }
    if (end == 1 + start) {
      return molecules.get(start);
    }
    if (end == 2 + start) {
      return getMcsOfPair(molecules.get(start), molecules.get(start + 1));
    }
    int middle = (start + end) / 2;

    Molecule leftMcs = getMcsHelper(molecules, start, middle);
    Molecule rightMcs = getMcsHelper(molecules, middle, end);

    return getMcsOfPair(leftMcs, rightMcs);
  }

  /**
   * Helper method to take MCS of exactly two molecules.
   */
  private Molecule getMcsOfPair(Molecule moleculeA, Molecule moleculeB) {
    MCS.setMolecules(moleculeA, moleculeB);
    return MCS.nextResult().getAsMolecule();
  }
}
