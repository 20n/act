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

  private static final String INCHI_IMPORT_SETTINGS = "inchi";

  // This SAR accepts every substrate
  protected static final List<Sar> NO_SAR = new ArrayList<>();

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
        // If there is an error on a certain RO, metabolite pair, we should log the error, but the expansion may
        // produce some valid results, so no error is thrown.
      } catch (ReactionException e) {
        LOGGER.error("ReactionException on getPredictions. %s", e.getMessage());
      } catch (IOException e) {
        LOGGER.error("IOException during prediction generation. %s", e.getMessage());
      }
    }

    return result;
  }

  /**
   * Filters the RO list to keep only those ROs with n substrates.
   *
   * @param roList The initial list of Ros.
   * @param n The number of substrates to screen for.
   * @return The subset of the ros which have exactly n substrates.
   */
  protected List<Ero> getNSubstrateRos(List<Ero> roList, int n) {
    List<Ero> nSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == n) {
        nSubstrateReactions.add(ro);
      }
    }

    LOGGER.info("Proceeding with %d %d substrate ROs.", nSubstrateReactions.size(), n);
    return nSubstrateReactions;
  }

  /**
   * This function imports a given inchi to a Molecule.
   *
   * @param inchi Input inchi.
   * @return The resulting Molecule.
   * @throws MolFormatException
   */
  protected Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }

}
