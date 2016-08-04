package com.act.biointerpretation.sars;

import chemaxon.core.ChemConst;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to filter the potential substrates of an enzyme based on their number of carbons.
 * This should generally not be used alone, but as an extra filter on top of a substructure based SAR.
 *
 * This is useful primarily to avoid matching very large, complicated molecules, to SARs for which all
 * of our evidence points to relatively small substrates. For example, if a substructure SAR was constructed on 5
 * substrates, with Carbon counts between 10 and 20, we wouldn't necessarily want to assume that the corresponding
 * enzyme would also act on a molecule with 100 Carbon atoms, even if that molecule matched the substructure SAR.
 */
public class OneSubstrateCarbonCountSar implements Sar {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateCarbonCountSar.class);

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
    return oneSubstrate.getAtomCount(ChemConst.C) >= minCount && oneSubstrate.getAtomCount(ChemConst.C) <= maxCount;
  }

  /**
   * TODO: Add a configurable fuzziness to the builder.
   * This would allow it to build a SAR to accept atoms with carbon counts within some range of the seen reactions'
   * counts, rather than only those strictly within the observed bounds.
   */
  public static class Factory implements SarFactory {

    @Override
    public Sar buildSar(List<RxnMolecule> reactions) {
      if (!DbAPI.areAllOneSubstrate(reactions)) {
        throw new IllegalArgumentException("Reactions are not all one substrate.");
      }

      List<Integer> carbonCounts = getSubstrateCarbonCounts(reactions);
      return new OneSubstrateCarbonCountSar(Collections.min(carbonCounts), Collections.max(carbonCounts));
    }


    private List<Integer> getSubstrateCarbonCounts(List<RxnMolecule> reactions) {
      return reactions.stream()
          .map(rxn -> rxn.getReactants()[0])
          .map(molecule -> molecule.getAtomCount(ChemConst.C))
          .collect(Collectors.toList());
    }
  }
}
