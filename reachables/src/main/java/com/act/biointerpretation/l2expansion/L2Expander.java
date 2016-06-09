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

/**
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites.
 */
public class L2Expander {

  private static final String NO_AUX_SETTING = "inchi:AuxNone";

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  List<Ero> roList;
  List<String> metaboliteList;

  /**
   * @param roList         A list of all Eros to be tested
   * @param metaboliteList An list of all metabolites on which to test the ROs.
   */
  public L2Expander(List<Ero> roList, List<String> metaboliteList) {
    this.roList = roList;
    this.metaboliteList = metaboliteList;
  }

  /**
   * Tests all reactions in roList on all metabolites in metaboliteList
   * TODO: extend this function to operate on ROs with more than one substrate
   * @return corpus of all reactions that are predicted to occur.
   * @throws IOException
   */
  public L2PredictionCorpus getPredictionCorpus() throws IOException {
    //throw out multiple substrate reactions
    roList = getSingleSubstrateReactions(roList);

    List<L2Prediction> results = new ArrayList<>();

    //iterate over every (metabolite, ro) pair
    for (String inchi : metaboliteList) {

      // Get Molecule from metabolite
      // Continue to next metabolite if this fails
      Molecule[] substrates;
      try {
        substrates = new Molecule[]{MolImporter.importMol(inchi, "inchi")};
      } catch (MolFormatException e) {
        LOGGER.error(e.getMessage(), "MolFormatException on metabolite:", inchi, e.getMessage());
        continue;
      }

      for (Ero ro : roList) {

        // Get reactor from ERO
        // Continue to next reactor if this fails
        Reactor reactor = new Reactor();
        try {
          reactor.setReactionString(ro.getRo());
        } catch (ReactionException e) {
          LOGGER.error("ReactionException on RO:", ro.getId(), e.getMessage());
          continue;
        }

        // Apply reactor to substrates if possible
        try {
          Molecule[] products = ReactionProjector.projectRoOnMolecules(substrates, reactor);

          if (products != null && products.length > 0) { //reaction worked if products are produced
            results.add(new L2Prediction(getInchis(substrates), ro, getInchis(products)));
          }

        } catch (ReactionException e) {
          LOGGER.error("ReactionException! Ro, metabolite:", ro.getRo(), inchi, e.getMessage());
        } catch (IOException e) {
          LOGGER.error("IOException on getting inchis for substrates or products.", e.getMessage());
        }
      }
    }
    return new L2PredictionCorpus(results);
  }


  /**
   * Filters the RO list to get rid of ROs with more than one substrate.
   * @param roList The initial list of Ros.
   * @return the subset of the Ros which have exactly one substrate.
   */
  private List<Ero> getSingleSubstrateReactions(List<Ero> roList) {
    int removalCount = 0;

    List<Ero> singleSubstrateReactions = new ArrayList<Ero>();
    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == 1) {
        singleSubstrateReactions.add(ro);
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
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private List<String> getInchis(Molecule[] mols) throws IOException {
    List<String> inchis = new ArrayList<>();
    for (int i = 0; i < mols.length; i++) {
      inchis.add(MolExporter.exportToFormat(mols[i], NO_AUX_SETTING));
    }
    return inchis;
  }
}

