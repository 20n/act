package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.chemaxon.search.mcs.MaxCommonSubstructure;
import com.chemaxon.search.mcs.McsSearchOptions;
import com.chemaxon.search.mcs.RingHandlingMode;

import java.util.List;

public class McsCalculator {

  /**
   * Bond matching = true --> only bonds of same order will match
   * connectedMode = true --> only return one fragment
   * ringHandlingMode = KEEP_RINGS --> don't allow a ring to be only partially matched by substructure; all or nothing
   * We do not match bond type because it throws off a lot of matches on benzene rings with bonds shifted.
   */
  public static final McsSearchOptions REACTION_BUILDING_OPTIONS =
      new McsSearchOptions.Builder()
          .bondTypeMatching(true)
          .connectedMode(false)
          .ringHandlingMode(RingHandlingMode.KEEP_RINGS)
          .build();
  public static final McsSearchOptions SAR_OPTIONS =
      new McsSearchOptions.Builder()
          .bondTypeMatching(false)
          .connectedMode(false)
          .ringHandlingMode(RingHandlingMode.KEEP_RINGS)
          .build();


  private final MaxCommonSubstructure mcs;

  public McsCalculator(McsSearchOptions mcsOptions) {
    this.mcs = MaxCommonSubstructure.newInstance(mcsOptions);
  }

  /**
   * Gets MCS of any number of molecules by iteratively applying Chemaxon's MCS search to all substrates.
   * For an array of n molecules, this will use n-1 MCS operations.
   * TODO: experiment with LibraryMcs instead of MaxCommonSubstructure here; it may find a better overall match.
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
