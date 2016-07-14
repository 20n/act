package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites.
 */
public class SingleSubstrateRoExpander implements L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SingleSubstrateRoExpander.class);
  private static final Integer ONE_SUBSTRATES = 1;
  private static final Integer CLEAN_DIMENSION = 2;
  private static final List<Sar> NO_SAR = new ArrayList<>();

  private List<Ero> roList;
  private List<String> metaboliteList;
  private PredictionGenerator generator;

  /**
   * @param roList A list of all ros to be tested
   * @param metaboliteList A list of all metabolites on which to test the ROs.
   */
  public SingleSubstrateRoExpander(List<Ero> roList, List<String> metaboliteList, PredictionGenerator generator) {
    this.roList = roList;
    this.metaboliteList = metaboliteList;
    this.generator = generator;
  }

  /**
   * This function imports, cleans and aromatizes an input inchi
   *
   * @param inchi Input inchi
   * @return A cleaned, aromatized molecule
   * @throws MolFormatException
   */
  private Molecule importCleanAndAromatizeMolecule(String inchi) throws MolFormatException {
    Molecule mol = MolImporter.importMol(inchi, "inchi");
    Cleaner.clean(mol, CLEAN_DIMENSION);
    mol.aromatize(MoleculeGraph.AROM_BASIC);
    return mol;
  }

  /**
   * Tests all reactions in roList on all metabolites in metaboliteList
   * TODO: extend this function to operate on ROs with more than one substrate
   *
   * @return corpus of all reactions that are predicted to occur.
   * @throws IOException
   */
  public L2PredictionCorpus getPredictions() {
    // Use only single substrate reactions
    List<Ero> singleSubstrateRoList = getSingleSubstrateReactions(roList);

    L2PredictionCorpus result = new L2PredictionCorpus();

    //iterate over every (metabolite, ro) pair
    for (String inchi : metaboliteList) {
      // Get Molecule from metabolite
      // Continue to next metabolite if this fails
      List<Molecule> singleSubstrateContainer;
      try {
        singleSubstrateContainer = Arrays.asList(importCleanAndAromatizeMolecule(inchi));
      } catch (MolFormatException e) {
        LOGGER.error("MolFormatException on metabolite %s. %s", inchi, e.getMessage());
        continue;
      }

      for (Ero ro : singleSubstrateRoList) {

        // Apply reactor to substrate if possible
        try {
          result.addAll(generator.getPredictions(singleSubstrateContainer, ro, NO_SAR));
          // If there is an error on a certain RO, metabolite pair, we should log the error, but the the expansion may
          // produce some valid results, so no error is thrown.
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
   * Filters the RO list to get rid of ROs with more or less than n substrates.
   *
   * @param roList The initial list of Ros.
   * @return The subset of the ros which have exactly n substrates.
   */
  private List<Ero> getSingleSubstrateReactions(List<Ero> roList) {
    List<Ero> oneSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == ONE_SUBSTRATES) {
        oneSubstrateReactions.add(ro);
      }
    }

    LOGGER.info("Proceeding with %d one substrate ROs.", oneSubstrateReactions.size());
    return oneSubstrateReactions;
  }
}

