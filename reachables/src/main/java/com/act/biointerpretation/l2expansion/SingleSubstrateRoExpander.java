package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
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
  private List<String> metaboliteList;

  /**
   * @param roList A list of all ros to be tested
   * @param metaboliteList A list of all metabolites on which to test the ROs.
   */
  public SingleSubstrateRoExpander(List<Ero> roList, List<String> metaboliteList, PredictionGenerator generator) {
    super(generator);
    this.roList = roList;
    this.metaboliteList = metaboliteList;
  }

  @Override
  public Iterable<PredictionSeed> getPredictionSeeds() {
    List<PredictionSeed> result = new ArrayList<>();

    // Use only single substrate reactions
    List<Ero> singleSubstrateRoList = getNSubstrateRos(roList, ONE_SUBSTRATES);

    for (Ero ro : singleSubstrateRoList) {

      SerializableReactor reactor;
      try {
        reactor = new SerializableReactor(ro.getReactor(), ro.getId(), NO_NAME);
      } catch (ReactionException e) {
        LOGGER.info("Skipping ro %d, couldn't get Reactor.", ro.getId());
        continue;
      }

      //iterate over every (metabolite, ro) pair
      for (String inchi : metaboliteList) {
        // Get Molecule from metabolite
        // Continue to next metabolite if this fails
        List<Molecule> singleSubstrateContainer;
        try {
          singleSubstrateContainer = Arrays.asList(importMolecule(inchi));
        } catch (MolFormatException e) {
          LOGGER.error("MolFormatException on metabolite %s. %s", inchi, e.getMessage());
          continue;
        }

        result.add(new PredictionSeed(singleSubstrateContainer, reactor, NO_SAR));
      }
    }

    return result;
  }

}

