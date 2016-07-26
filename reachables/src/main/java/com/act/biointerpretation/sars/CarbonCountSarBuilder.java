package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;

import java.util.List;

public class CarbonCountSarBuilder implements SarBuilder {

  private static final Integer CARBON = 6;

  private final DbAPI dbApi;

  public CarbonCountSarBuilder(DbAPI dbApi) {
    this.dbApi = dbApi;
  }

  @Override
  public Sar buildSar(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> substrates = dbApi.getFirstSubstratesAsMolecules(reactions);
    return new CarbonCountSar(getMinCarbonCount(substrates), getMaxCarbonCount(substrates));
  }

  private Integer getMaxCarbonCount(List<Molecule> molecules) {
    Integer maxCount = 0;
    for (Molecule mol : molecules) {
      if (mol.getAtomCount(CARBON) > maxCount) {
        maxCount = mol.getAtomCount(CARBON);
      }
    }
    return maxCount;
  }


  private Integer getMinCarbonCount(List<Molecule> molecules) {
    Integer minCount = Integer.MAX_VALUE;
    for (Molecule mol : molecules) {
      if (mol.getAtomCount(CARBON) < minCount) {
        minCount = mol.getAtomCount(CARBON);
      }
    }
    return minCount;
  }
}
