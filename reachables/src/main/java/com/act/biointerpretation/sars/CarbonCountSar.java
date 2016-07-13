package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CarbonCountSar implements Sar {

  private static final int CARBON = 4;

  @JsonProperty("min_carbon_count")
  private int minCount;

  @JsonProperty("max_carbon_count")
  private int maxCount;

  /**
   * For JSON.
   */
  private CarbonCountSar(){}

  public CarbonCountSar(int minCount, int maxCount) {
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
}
