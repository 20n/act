package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  private List<Ero> roList;
  private List<String> metaboliteList;

  /**
   * @param roList         A list of all ros to be tested
   * @param metaboliteList A list of all metabolites on which to test the ROs.
   */
  public L2Expander(List<Ero> roList, List<String> metaboliteList) {
    this.roList = roList;
    this.metaboliteList = metaboliteList;
  }

  /**
   * Tests all reactions in roList on all metabolites in metaboliteList
   * TODO: extend this function to operate on ROs with more than one substrate
   *
   * @return corpus of all reactions that are predicted to occur.
   * @throws IOException
   */
  public L2PredictionCorpus getSingleSubstratePredictionCorpus() throws IOException {
    // Use only single substrate reactions
    List<Ero> singleSubstrateRoList = getSingleSubstrateReactions(roList);
    LOGGER.info("Proceeding with %d single substrate ROs.", roList.size());

    L2PredictionCorpus result = new L2PredictionCorpus();
    Integer predictionId = 0;

    //iterate over every (metabolite, ro) pair
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
        // Get reactor from ro
        // Continue to next reactor if this fails
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

            result.addPrediction(new L2Prediction(
                predictionId,
                getPredictionChemicals(singleSubstrateContainer),
                new L2PredictionRo(ro.getId(), ro.getRo()),
                getPredictionChemicals(products)));
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

  public L2PredictionCorpus getTwoSubstratePredictionCorpus(List<String> chemicalsOfInterest, MongoDB db)
      throws IOException, ReactionException {

    List<Ero> listOfRos = getNSubstrateReactions(roList, 2);

    LOGGER.info("The number of ROs to apply are %d", listOfRos.size());
    LOGGER.info("Construct mapping between ro and it's corresponding reactor");

    Map<Ero, Reactor> roToReactor = new HashMap<>();
    for (Ero ro : listOfRos) {
      Reactor reactor = new Reactor();
      try {
        reactor.setReactionString(ro.getRo());
      } catch (ReactionException e) {
        LOGGER.error("ReactionException on RO %d. %s", ro.getId(), e.getMessage());
        continue;
      }
      roToReactor.put(ro, reactor);
    }

    Map<Chemical, Molecule> chemicalsOfInterestChemicalToMoleculeMapping = new HashMap<>();
    Map<Chemical, Molecule> metabolitesChemicalToMolecule = new HashMap<>();

    Map<Integer, Set<Long>> roIdToChemicalIds = new HashMap<>();

    LOGGER.info("Construct mapping between inchi's chemical to it's molecule representation. We do l2 expansion on the" +
        "molecular representation. Also construct mapping between roId to chemical id.");

    /**
     * We currently have a mapping from chemical -> set of RO ids that have relevance to the chemical's structure, ie
     * the chemical matched with one of the substrates of the reaction. We need to reverse this mapping to get ro id ->
     * chemical id since we iterate through the ro id in a later step. We do this for both the chemicalsOfInterest and
     * the metaboliteList. However, we store the transformed chemicals in the two lists separately.
     */

    List<String>[] chemicalsOfInterestAndMetabolitesList = new List[] { chemicalsOfInterest, metaboliteList };
    Boolean isChemicalsOfInterest = true;

    for (List<String> listOfInchis : chemicalsOfInterestAndMetabolitesList) {
      for (String inchi : listOfInchis) {
        Chemical chemical = db.getChemicalFromInChI(inchi);
        if (chemical == null) {
          continue;
        }

        for (Integer roId : chemical.getSubstructureRoIds()) {
          Set<Long> chemIds = roIdToChemicalIds.get(roId);
          if (chemIds == null) {
            chemIds = new HashSet<>();
            roIdToChemicalIds.put(roId, chemIds);
          }
          chemIds.add(chemical.getUuid());
        }

        try {
          // Import and clean the molecule.
          Molecule mol = MolImporter.importMol(inchi, "inchi");
          Cleaner.clean(mol, 2);
          mol.aromatize(MoleculeGraph.AROM_BASIC);

          if (isChemicalsOfInterest) {
            chemicalsOfInterestChemicalToMoleculeMapping.put(chemical, mol);
          } else {
            metabolitesChemicalToMolecule.put(chemical, mol);
          }
        } catch (MolFormatException e) {
          LOGGER.error(e.getMessage(), "MolFormatException on metabolite %s. %s", inchi, e.getMessage());
        }
      }

      isChemicalsOfInterest = false;
    }

    LOGGER.info("Perform L2 expansion for each ro in the list");

    L2PredictionCorpus result = new L2PredictionCorpus();
    int roProcessedCounter = 0;

    int predictionId = 0;
    for (Ero ro : listOfRos) {
      roProcessedCounter++;
      LOGGER.info("Processing the %d indexed ro out of %s ros", roProcessedCounter, listOfRos.size());

      // TODO: We only compute combinations of chemical of interest and metabolites, while not doing exclusive pairwise
      // comparisons of only chemicals of interest or only metabolites. We do not care of pairwise operations of metabolites
      // since the output of that dataset is not interesting (the cell should be doing that anyways). However, pairwise
      // operations of chemicals of interest might be interesting edge cases ie ro takes in two of the same molecules
      // and outputs something novel. We do not do that here since it would add to the already long time this function
      // takes to execute.
      for (Map.Entry<Chemical, Molecule> chemToMolInterests : chemicalsOfInterestChemicalToMoleculeMapping.entrySet()) {
        for (Map.Entry<Chemical, Molecule> chemToMolMetabolites : metabolitesChemicalToMolecule.entrySet()) {

          if (roIdToChemicalIds.get(ro.getId()) == null) {
            continue;
          }

          Long chemicalOfInterestId = chemToMolInterests.getKey().getUuid();
          Long metaboliteId = chemToMolMetabolites.getKey().getUuid();

          // If either of the two substrates are not found in the RO to Chemical mappings, we know that we cannot do
          // L2 expansion on that pair of molecules since one of their substructures does not match any of the substrates
          // in the reaction. Therefore, skip it!
          if (!roIdToChemicalIds.get(ro.getId()).contains(chemicalOfInterestId) ||
              !roIdToChemicalIds.get(ro.getId()).contains(metaboliteId)) {
            continue;
          }

          Molecule[] substrates = new Molecule[2];
          substrates[0] = chemToMolInterests.getValue();
          substrates[1] = chemToMolMetabolites.getValue();

          List<Molecule[]> products = ReactionProjector.fastProjectionOfTwoSubstrateRoOntoTwoMolecules(substrates, roToReactor.get(ro));
          for (Molecule[] product : products) {
            result.addPrediction(new L2Prediction(predictionId, getInchis(substrates), ro, getInchis(product)));
            predictionId++;
          }
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
  private List<Ero> getNSubstrateReactions(List<Ero> roList, int n) {

    int removalCount = 0;
    List<Ero> singleSubstrateReactions = new ArrayList<Ero>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == n) {
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
   * Filters the RO list to get rid of ROs with more than one substrate.
   *
   * @param roList The initial list of Ros.
   * @return The subset of the ros which have exactly one substrate.
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
   * Translate an array of chemaxon Molecules into a list of L2PredictionChemicals
   *
   * @param mols An array of molecules.
   * @return An array of L2PredictionChemicals corresponding to the supplied molecules.
   */
  private List<L2PredictionChemical> getPredictionChemicals(Molecule[] mols) throws IOException {
    List<L2PredictionChemical> l2PredictionChemicals = new ArrayList<>();
    for (Molecule mol : mols) {
      l2PredictionChemicals.add(new L2PredictionChemical(MolExporter.exportToFormat(mol, INCHI_SETTINGS)));
    }
    return l2PredictionChemicals;
  }
}

