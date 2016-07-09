package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.chemaxon.search.mcs.MaxCommonSubstructure;
import com.jacob.com.NotImplementedException;

import java.util.List;

public class McsCalculator {

  public Molecule getMCS(List<Molecule> substrates) {
    MaxCommonSubstructure mcs = MaxCommonSubstructure.newInstance();

    if (substrates.size() < 2) {
      throw new IllegalArgumentException("Can't calculate MCS for fewer than 2 molecules.");
    }
    if (substrates.size() > 2) {
      throw new NotImplementedException("McsCalculator only implemented for exactly 2 substrates.");
    }

    for (Molecule substrate : substrates) {
      substrate.aromatize(MoleculeGraph.AROM_BASIC);
    }

    mcs.setMolecules(substrates.get(0), substrates.get(1));
    return mcs.nextResult().getAsMolecule();
  }
}
