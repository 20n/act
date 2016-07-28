package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

import java.util.Collections;
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

  /**
   * TODO: Add a configurable fuzziness to the builder.
   * This would allow it to build a SAR to accept atoms with carbon counts within some range of the seen reactions'
   * counts, rather than only those strictly within the observed bounds.
   */
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

      List<Integer> carbonCounts = getSubstrateCarbounCounts(reactions);
      return new OneSubstrateCarbonCountSar(Collections.min(carbonCounts), Collections.max(carbonCounts));
    }


    private List<Integer> getSubstrateCarbounCounts(List<Reaction> reactions) {
      List<RxnMolecule> rxnMolecules = dbApi.getRxnMolecules(reactions);
      List<Molecule> substrates = Lists.transform(rxnMolecules, rxn -> rxn.getReactants()[0]);
      return Lists.transform(substrates, molecule -> molecule.getAtomCount());
    }
  }
}
