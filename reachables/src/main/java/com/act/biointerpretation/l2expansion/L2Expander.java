package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  // This SAR accepts every substrate
  protected static final Sar NO_SAR = new Sar() {
    @Override
    public boolean test(List<Molecule> substrates) {
      return true;
    }
  };

  private PredictionGenerator generator;

  public abstract Iterable<PredictionSeed> getPredictionSeeds();

  public L2Expander(PredictionGenerator generator) {
    this.generator = generator;
  }

  public L2PredictionCorpus getPredictions() {
    L2PredictionCorpus result = new L2PredictionCorpus();

    for (PredictionSeed seed : getPredictionSeeds()) {
      // Apply reactor to substrate if possible
      try {
        result.addAll(generator.getPredictions(seed));
        // If there is an error on a certain RO, metabolite pair, we should log the error, but the the expansion may
        // produce some valid results, so no error is thrown.
      } catch (ReactionException e) {
        LOGGER.error("ReactionException during prediction generation. %s", e.getMessage());
      } catch (IOException e) {
        LOGGER.error("IOException during prediction generation. %s", e.getMessage());
      }
    }

    return result;
  }

  /**
   * Filters the RO list to get rid of ROs with more or less than n substrates.
   *
   * @param roList The initial list of Ros.
   * @return The subset of the ros which have exactly n substrates.
   */
  protected List<Ero> getNSubstrateReactions(List<Ero> roList, int n) {
    List<Ero> oneSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == n) {
        oneSubstrateReactions.add(ro);
      }
    }

    LOGGER.info("Proceeding with %d one substrate ROs.", oneSubstrateReactions.size());
    return oneSubstrateReactions;
  }

  /**
   * This function imports a given inchi to a Molecule.
   *
   * @param inchi Input inchi.
   * @return The resulting Molecule.
   * @throws MolFormatException
   */
  protected Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, "inchi");
  }

}
