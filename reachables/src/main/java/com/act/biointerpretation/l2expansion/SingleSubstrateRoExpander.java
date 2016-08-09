package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites.
 */
public class SingleSubstrateRoExpander extends L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SingleSubstrateRoExpander.class);
  private static final Integer ONE_SUBSTRATES = 1;
  private List<Ero> roList;
  private List<Molecule> substrates;
  private static final Integer DEFAULT_MASS_CUTOFF = 950; // Don't project on anything > 950 daltons, it takes forever.

  private Integer massCutoff;

  /**
   * @param roList A list of all ros to be tested
   * @param substrates A list of all metabolites on which to test the ROs.
   */
  public SingleSubstrateRoExpander(List<Ero> roList, List<Molecule> substrates, PredictionGenerator generator) {
    super(generator);
    this.roList = roList;
    this.substrates = substrates;
    this.massCutoff = DEFAULT_MASS_CUTOFF;
  }

  @Override
  public Iterable<PredictionSeed> getPredictionSeeds() {
    List<PredictionSeed> result = new ArrayList<>();

    // Use only single substrate reactions
    List<Ero> singleSubstrateRoList = getNSubstrateRos(roList, ONE_SUBSTRATES);

    for (Ero ro : singleSubstrateRoList) {
      SerializableReactor reactor;
      try {
        reactor = new SerializableReactor(ro.getReactor(), ro.getId());
      } catch (ReactionException e) {
        LOGGER.info("Skipping ro %d, couldn't get Reactor.", ro.getId());
        continue;
      }

      //iterate over every (metabolite, ro) pair
      for (Molecule substrate : substrates) {
        result.add(new PredictionSeed(ro.getId().toString(), Arrays.asList(substrate), reactor, NO_SAR));
      }
    }

    LOGGER.info("Created %d prediction seeds", result.size());
    return result;
  }
}

