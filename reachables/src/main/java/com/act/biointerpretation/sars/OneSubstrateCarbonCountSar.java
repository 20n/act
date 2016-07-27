package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

import java.util.List;

public class OneSubstrateCarbonCountSar implements Sar {

  private static final int CARBON = 6;

  @JsonProperty("min_carbon_count")
  private int minCount;

  @JsonProperty("max_carbon_count")
  private int maxCount;

  /**
   * For JSON.
   */
  private OneSubstrateCarbonCountSar() {
  }

  public OneSubstrateCarbonCountSar(int minCount, int maxCount) {
    this.minCount = minCount;
    this.maxCount = maxCount;
  }

  public int getMinCount() {
    return minCount;
  }

  /**
   * For JSON.
   */
  private void setMinCount(int minCount) {
    this.minCount = minCount;
  }

  public int getMaxCount() {
    return maxCount;
  }

  /**
   * For JSON.
   */
  private void setMaxCount(int maxCount) {
    this.maxCount = maxCount;
  }

  @Override
  public boolean test(List<Molecule> substrates) {
    for (Molecule molecule : substrates) {
      if (molecule.getAtomCount(CARBON) < minCount || molecule.getAtomCount(CARBON) > maxCount) {
        return false;
      }
    }
    return true;
  }

  public static class Builder implements SarBuilder {

    private static final Integer CARBON = 6;

    private final DbAPI dbApi;

    public Builder(DbAPI dbApi) {
      this.dbApi = dbApi;
    }

    @Override
    public Sar buildSar(List<Reaction> reactions) throws MolFormatException {
      if (!DbAPI.areAllOneSubstrate(reactions)) {
        throw new MolFormatException("Reactions are not all one substrate.");
      }

      List<RxnMolecule> rxnMolecules = dbApi.getRxnMolecules(reactions);
      List<Molecule> substrates = Lists.transform(rxnMolecules, rxn -> rxn.getReactants()[0]);
      return new OneSubstrateCarbonCountSar(getMinCarbonCount(substrates), getMaxCarbonCount(substrates));
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
}
