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

  /**
   * We do not match bond type because it throws off a lot of matches on benzene rings with bonds shifted.
   * We choose KEEP_RINGS to preserve the "spirit" of a molecule with a ring: i.e. if two molecules
   * both have a ring, but one has a nitrogen in the ring, we don't want to conclude that we found a SAR
   * indicating that the enzyme only needs to match a 5 carbon chain. Instead this option forces us to not
   * match that ring, which may end up causing such a SAR to be filtered out for now as uninformative.
   */
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
   * Gets MCS of any number of molecules by iteratively applying Chemaxon's MCS search to all substrates.
   * For an array of n molecules, this will use n-1 MCS operations, but the hope is that they will
   * get faster as we go because we'll be computing between the prefix MCS and a new molecule,
   * rather than two full molecules.
   *
   * @param molecules The molecules to get the MCS of.
   * @return The MCS of all input molecules.
   */
  public Molecule getMCS(List<Molecule> molecules) {
    if (molecules.isEmpty()) {
      throw new IllegalArgumentException("Cannot get MCS of empty list of molecules.");
    }

    Molecule substructure = molecules.get(0);
    for (Molecule mol : molecules.subList(1, molecules.size())) {
      substructure = getMcsOfPair(substructure, mol);
    }
    return substructure;
  }

  /**
   * Helper method to find MCS of exactly two molecules.
   */
  private Molecule getMcsOfPair(Molecule moleculeA, Molecule moleculeB) {
    mcs.setMolecules(moleculeA, moleculeB);
    return mcs.nextResult().getAsMolecule();
  }
}
