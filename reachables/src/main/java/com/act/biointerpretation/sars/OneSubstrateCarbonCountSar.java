package com.act.biointerpretation.sars;

import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OneSubstrateCarbonCountSar implements Sar {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateCarbonCountSar.class);
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
    // This class of SARs is only valid on single-substrate reactions.
    if (substrates.size() != 1) {
      return false;
    }

    Molecule oneSubstrate = substrates.get(0);
    LOGGER.info("Min, max, count: %d, %d, %d", minCount, maxCount, oneSubstrate.getAtomCount(CARBON));
    LOGGER.info("Return value: %b", oneSubstrate.getAtomCount(CARBON) >= minCount && oneSubstrate.getAtomCount(CARBON) <= maxCount);
    return oneSubstrate.getAtomCount(CARBON) >= minCount && oneSubstrate.getAtomCount(CARBON) <= maxCount;
  }

  /**
   * TODO: Add a configurable fuzziness to the builder.
   * This would allow it to build a SAR to accept atoms with carbon counts within some range of the seen reactions'
   * counts, rather than only those strictly within the observed bounds.
   */
  public static class Builder implements SarBuilder {

    private static final Integer CARBON = 6;

    @Override
    public Sar buildSar(List<RxnMolecule> reactions) throws MolFormatException {
      if (!DbAPI.areAllOneSubstrate(reactions)) {
        throw new MolFormatException("Reactions are not all one substrate.");
      }

      List<Integer> carbonCounts = getSubstrateCarbonCounts(reactions);
      return new OneSubstrateCarbonCountSar(Collections.min(carbonCounts), Collections.max(carbonCounts));
    }


    private List<Integer> getSubstrateCarbonCounts(List<RxnMolecule> reactions) {
      return reactions.stream()
          .map(rxn -> rxn.getReactants()[0])
          .map(molecule -> molecule.getAtomCount(CARBON))
          .collect(Collectors.toList());
    }
  }
}
