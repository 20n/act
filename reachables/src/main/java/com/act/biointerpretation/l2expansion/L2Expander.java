package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ConcurrentReactorProcessor;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.util.iterator.MoleculeIterator;
import chemaxon.util.iterator.MoleculeIteratorFactory;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  List<Ero> roList;
  List<String> metaboliteList;

  /**
   * @param roList         A list of all Eros to be tested
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
    //throw out multiple substrate reactions
    this.roList = getNSubstrateReactions(roList, 1);

    L2PredictionCorpus result = new L2PredictionCorpus();

    //iterate over every (metabolite, ro) pair
    for (String inchi : metaboliteList) {

      // Get Molecule from metabolite
      // Continue to next metabolite if this fails
      Molecule[] singleSubstrateContainer;
      try {
        singleSubstrateContainer = new Molecule[]{MolImporter.importMol(inchi, "inchi")};
      } catch (MolFormatException e) {
        LOGGER.error(e.getMessage(), "MolFormatException on metabolite %s. %s", inchi, e.getMessage());
        continue;
      }

      for (Ero ro : roList) {

        // Get reactor from ERO
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
            result.addPrediction(new L2Prediction(getInchis(singleSubstrateContainer), ro, getInchis(products)));
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

  public L2PredictionCorpus getMultipleSubstratePredictionCorpus(List<String> chemicalsOfInterest, int substrateCount)
      throws IOException, ReactionException {
    //throw out multiple substrate reactions
    List<Ero> listOfRos = getNSubstrateReactions(roList, substrateCount);

    List<Ero> listOfRos2 = new ArrayList<>();
    int counter = 0;
    for (Ero ro : listOfRos) {
      listOfRos2.add(ro);
      if (counter > 10) {
        break;
      }
      counter++;
    }

    L2PredictionCorpus result = new L2PredictionCorpus();

    //List<String> metabolitePlusChemicalsOfInterest = new ArrayList<>(metaboliteList);
    List<String> metabolitePlusChemicalsOfInterest = new ArrayList<>();

    if (chemicalsOfInterest.size() > 0) {
      metabolitePlusChemicalsOfInterest.addAll(chemicalsOfInterest);
    }

    List<Molecule> transformedMolecules = new ArrayList<>();
    for (String inchi : metabolitePlusChemicalsOfInterest) {
      try {
        transformedMolecules.add(MolImporter.importMol(inchi, "inchi"));
      } catch (MolFormatException e) {
        LOGGER.error(e.getMessage(), "MolFormatException on metabolite %s. %s", inchi, e.getMessage());
        continue;
      }
    }

    Molecule[] mols = new Molecule[transformedMolecules.size()];
    transformedMolecules.toArray(mols);

    for (Ero ro : listOfRos2) {
      Reactor reactor = new Reactor();
      try {
        reactor.setReactionString(ro.getRo());
      } catch (ReactionException e) {
        LOGGER.error("ReactionException on RO %d. %s", ro.getId(), e.getMessage());
        continue;
      }

      ConcurrentReactorProcessor reactorProcessor = new ConcurrentReactorProcessor();
      reactorProcessor.setReactor(reactor);

      // This step is needed by ConcurrentReactorProcessor for the combinatorial exploration.
      // The iterator takes in the same substrates in each iteration step to build a matrix of combinations.
      // For example, if we have A + B -> C, the iterator creates this array: [[A,B], [A,B]]. Based on this,
      // ConcurrentReactorProcessor will cross the elements in the first index with the second, creating the combinations
      // A+A, A+B, B+A, B+B and operates all of those on the RO, which is what is desired.
      MoleculeIterator[] iterator = new MoleculeIterator[2];
      for (int i = 0; i < 2; i++) {
        iterator[i] = MoleculeIteratorFactory.createMoleculeIterator(mols);
      }

      reactorProcessor.setReactantIterators(iterator, ConcurrentReactorProcessor.MODE_COMBINATORIAL);

      List<Molecule[]> results = null;
      int reactantCombination = 0;
      while ((results = reactorProcessor.reactNext()) != null) {
        reactantCombination++;
        if (results.size() == 0) {
          LOGGER.debug("No results found for reactants combination %d, skipping", reactantCombination);
          continue;
        }

        for (Molecule[] products : results) {
          result.addPrediction(new L2Prediction(getInchis(reactorProcessor.getReactants()), ro, getInchis(products)));
        }
      }
      break;
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

