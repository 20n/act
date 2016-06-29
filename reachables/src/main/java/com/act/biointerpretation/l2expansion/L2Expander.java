package com.act.biointerpretation.l2expansion;

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
import java.util.Arrays;
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
  private static final Integer ONE_SUBSTRATES = 1;
  private static final Integer TWO_SUBSTRATES = 2;
  private static final Integer TWO_DIMENSION = 2;

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
   * This function imports, cleans and aromatizes an input inchi
   * @param inchi Input inchi
   * @return A cleaned, aromatized molecule
   * @throws MolFormatException
   */
  private Molecule importCleanAndAromatizeMolecule(String inchi) throws MolFormatException {
    Molecule mol = MolImporter.importMol(inchi, "inchi");
    Cleaner.clean(mol, TWO_DIMENSION);
    mol.aromatize(MoleculeGraph.AROM_BASIC);
    return mol;
  }

  /**
   * This function constructs a ro to set of molecules map
   * @param chemicals List of chemicals to process
   * @return A map of ro to set of molecules that match the ro's substructure
   */
  private Map<Integer, Set<Molecule>> constructRoToMolecules(List<Chemical> chemicals) {
    Map<Integer, Set<Molecule>> result = new HashMap<>();
    for (Chemical chemical : chemicals) {
      try {
        // Import and clean the molecule.
        Molecule mol = importCleanAndAromatizeMolecule(chemical.getInChI());

        if (mol == null) {
          continue;
        }

        for (Integer roId : chemical.getSubstructureRoIds()) {
          Set<Molecule> molecules = result.get(roId);
          if (molecules == null) {
            molecules = new HashSet<>();
            result.put(roId, molecules);
          }
          molecules.add(mol);
        }
      } catch (MolFormatException e) {
        LOGGER.error(e.getMessage(), "MolFormatException on metabolite %s. %s", chemical.getInChI(), e.getMessage());
      }
    }
    return result;
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
    List<Ero> singleSubstrateRoList = getNSubstrateReactions(roList, ONE_SUBSTRATES);

    L2PredictionCorpus result = new L2PredictionCorpus();
    Integer predictionId = 0;

    //iterate over every (metabolite, ro) pair
    for (String inchi : metaboliteList) {

      // Get Molecule from metabolite
      // Continue to next metabolite if this fails
      Molecule[] singleSubstrateContainer;
      try {
        singleSubstrateContainer = new Molecule[]{importCleanAndAromatizeMolecule(inchi)};
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

  /**
   * This function performs pairwise L2 expansion on two sets of substrates: an input chemical list and the metabolite list.
   * The function is optimized for only computing RO expansions on chemical combinations where both chemicals have passed
   * the RO substructure matching.
   * @param chemicalsOfInterest A list of chemicals to operate on
   * @param metabolites A list of metabolite molecules
   * @return A L2PredictionCorpus of all products generated
   * @throws IOException
   * @throws ReactionException
   */
  public L2PredictionCorpus getTwoSubstratePredictionCorpus(List<Chemical> chemicalsOfInterest, List<Chemical> metabolites)
      throws IOException, ReactionException {

    List<Ero> listOfRos = getNSubstrateReactions(roList, TWO_SUBSTRATES);

    LOGGER.info("The number of ROs to apply are %d", listOfRos.size());

    LOGGER.info("Constructing ro to molecule structures for metabolite list and chemicals of interest list.");
    Map<Integer, Set<Molecule>> roIdToMoleculesOfInterest = constructRoToMolecules(chemicalsOfInterest);
    Map<Integer, Set<Molecule>> roIdToMetabolites = constructRoToMolecules(metabolites);

    LOGGER.info("Perform L2 expansion for each ro in the list");
    L2PredictionCorpus result = new L2PredictionCorpus();
    int roProcessedCounter = 0;

    int predictionId = 0;
    for (Ero ro : listOfRos) {
      roProcessedCounter++;
      LOGGER.info("Processing the %d indexed ro out of %s ros", roProcessedCounter, listOfRos.size());

      // TODO: We only compute combinations of chemical of interest and metabolites, while not doing exclusive pairwise
      // comparisons of ONLY chemicals of interest or only metabolites. We do not care of pairwise operations of metabolites
      // since the output of that dataset is not interesting (the cell should be doing that anyways). However, pairwise
      // operations of chemicals of interest might be interesting edge cases ie ro takes in two of the same molecules
      // and outputs something novel. We do not do that here since it would add to the already long time this function
      // takes to execute.
      Set<Molecule> roMetabolitesSet = roIdToMetabolites.get(ro.getId());
      Set<Molecule> roMoleculesOfInterestSet = roIdToMoleculesOfInterest.get(ro.getId());

      if (roMetabolitesSet == null || roMoleculesOfInterestSet == null) {
        continue;
      }

      for (Molecule metabolite : roMetabolitesSet) {
        for (Molecule chemical : roMoleculesOfInterestSet) {
          Molecule[] substrates = new Molecule[] {metabolite, chemical};
          Reactor reactor = new Reactor();
          try {
            reactor.setReactionString(ro.getRo());
          } catch (ReactionException e) {
            LOGGER.error("ReactionException on RO %d. %s", ro.getId(), e.getMessage());
            continue;
          }

          Map<Molecule[], Molecule[]> substrateToProduct = ReactionProjector.fastProjectionOfTwoSubstrateRoOntoTwoMolecules(substrates, reactor);
          for (Map.Entry<Molecule[], Molecule[]> subToProd : substrateToProduct.entrySet()) {

            List<L2PredictionChemical> predictedSubstrates =
                L2PredictionChemical.getPredictionChemicals(getInchis(subToProd.getKey()));

            List<L2PredictionChemical> predictedProducts =
                L2PredictionChemical.getPredictionChemicals(getInchis(subToProd.getValue()));

            L2PredictionRo predictionRo = new L2PredictionRo(ro.getId(), ro.getRo());

            result.addPrediction(new L2Prediction(predictionId, predictedSubstrates, predictionRo, predictedProducts));
            predictionId++;
          }
        }
      }
    }

    return result;
  }

  /**
   * Filters the RO list to get rid of ROs with more or less than n substrates.
   * @param roList The initial list of Ros.
   * @param n The num of substrates to match against
   * @return The subset of the ros which have exactly n substrates.
   */
  private List<Ero> getNSubstrateReactions(List<Ero> roList, int n) {
    int removalCount = 0;
    List<Ero> nSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == n) {
        nSubstrateReactions.add(ro);
      } else {
        removalCount++;
      }
    }

    LOGGER.info("Removed %d ROs that had multiple substrates.", removalCount);
    LOGGER.info("Proceeding with %d ROs.", nSubstrateReactions.size());
    return nSubstrateReactions;
  }

  /**
   * Translate an array of chemaxon Molecules into an ArrayList of their String inchi representations
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

  /**
   * Translate an array of chemaxon Molecules into an ArrayList of their String inchi representations
   *
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private List<String> getInchis(Molecule[] mols) throws IOException {
    List<String> inchis = new ArrayList<>();
    for (Molecule mol : mols) {
      inchis.add(MolExporter.exportToFormat(mol, INCHI_SETTINGS));
    }
    return inchis;
  }
}

