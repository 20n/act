package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites.
 */
public class L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  private static final String INCHI_SETTINGS = new StringBuilder("inchi:").
      append("SAbs").append(','). // Force absolute stereo to ensure standard InChIs are produced.
      append("AuxNone").append(','). // Don't write the AuxInfo block.
      append("Woff"). // Disable warnings.
      toString();

  private List<Ero> roCorpus;
  private List<Integer> roIdList;
  private List<String> metaboliteList;

  /**
   * @param roIdList         A list of all Eros to be tested
   * @param metaboliteList A list of all metabolites on which to test the ROs.
   */
  public L2Expander(List<Ero> roCorpus, List<Integer> roIdList, List<String> metaboliteList) {
    this.roCorpus = roCorpus;
    this.roIdList = roIdList;
    this.metaboliteList = metaboliteList;
  }

  /**
   * Tests all reactions in roIdList on all metabolites in metaboliteList
   * TODO: extend this function to operate on ROs with more than one substrate
   *
   * @return corpus of all reactions that are predicted to occur.
   * @throws IOException
   */
  public L2PredictionCorpus getSingleSubstratePredictionCorpus() throws IOException {
    // Use only single substrate reactions
    List<Ero> singleSubstrateRoList = getSingleSubstrateReactions(roList);

    L2PredictionCorpus result = new L2PredictionCorpus();
    Integer predictionId = 0;

    //iterate over every (metabolite, roId) pair
    for (String inchi : metaboliteList) {

      // Get Molecule from metabolite
      // Continue to next metabolite if this fails
      Molecule[] singleSubstrateContainer;
      try {
        singleSubstrateContainer = new Molecule[]{MolImporter.importMol(inchi, "inchi")};
      } catch (MolFormatException e) {
        LOGGER.error("MolFormatException on metabolite %s. %s", inchi, e.getMessage());
        continue;
      }

      for (Ero ro : singleSubstrateRoList) {

        // Get reactor from roId Id
        // Continue to next reactor if this fails
        Ero ro = getRo(roId);
        Reactor reactor = new Reactor();
        try {
          reactor.setReactionString(ro.getRo());
        } catch (ReactionException e) {
          LOGGER.error("ReactionException on RO %d. %s", ro.getId(), e.getMessage());
          continue;
        }

        // Apply reactor to substrate if possible
        try {
          Molecule[] products = ReactionProjector.projectRoOnMolecules(singleSubstrateContainer, reactor);

          if (products != null && products.length > 0) { //reaction worked if products are produced

            result.addPrediction(new L2Prediction(predictionId,
                    getPredictedChemicals(singleSubstrateContainer), roId, getPredictedChemicals(products)));
            predictionId++;
          }

        } catch (ReactionException e) {
          LOGGER.error("ReactionException! Ro, metabolite: %s, %s. %s", ro.getRo(), inchi, e.getMessage());
        } catch (IOException e) {
          LOGGER.error("IOException on getting inchis for substrate or products. %s", e.getMessage());
        }
      }
    }

    return result;
  }


  /**
   * Filters the RO list to get rid of ROs with more than one substrate.
   *
   * @param roList The initial list of Ros.
   * @return The subset of the ros which have exactly one substrate.
   */
  private List<Integer> getSingleSubstrateReactions(List<Integer> roList) {

    int removalCount = 0;
    List<Integer> singleSubstrateReactions = new ArrayList<>();

    for (Integer roId : roList) {
      Ero ro = roCorpus.get(roId);
      if (ro.getSubstrate_count() == 1) {
        singleSubstrateReactions.add(roId);
      } else {
        removalCount++;
      }
    }

    LOGGER.info("Removed %d ROs that had multiple substrates.", removalCount);
    LOGGER.info("Proceeding with %d ROs.", singleSubstrateReactions.size());
    return singleSubstrateReactions;
  }

  /**
   * Translate an array of chemaxon Molecules into an ArrayList of their String inchi representations
   *
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private List<L2PredictionChemical> getPredictedChemicals(Molecule[] mols) throws IOException {
    List<L2PredictionChemical> inchis = new ArrayList<>();
    for (Molecule mol : mols) {
      inchis.add(new L2PredictionChemical(MolExporter.exportToFormat(mol, INCHI_SETTINGS)));
    }
    return inchis;
  }

  private Ero getRo(Integer roId) {
    for (Ero ro : roCorpus) {
      if (ro.getId().equals(roId)) {
        return ro;
      }
    }
    throw new IllegalArgumentException("Didn't find roId with id " + Integer.toString(roId) + " in corpus.");
  }
}

