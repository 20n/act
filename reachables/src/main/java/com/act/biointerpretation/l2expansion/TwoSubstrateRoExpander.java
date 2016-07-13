package com.act.biointerpretation.l2expansion;

import act.shared.Chemical;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TwoSubstrateRoExpander implements L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(TwoSubstrateRoExpander.class);
  private static final Integer CLEAN_DIMENSION = 2;
  private static final Integer TWO_SUBSTRATES = 2;
  private static final Optional<Sar> NO_SAR = Optional.empty();
  /**
   * This function performs pairwise L2 expansion on two sets of substrates: an input chemical list and the
   * metabolite list. The function is optimized for only computing RO expansions on chemical combinations
   * where both chemicals have passed the RO substructure matching.
   *
   * @param chemicalsOfInterest A list of chemicals to operate on
   * @param metabolites         A list of metabolite molecules
   * @return A L2PredictionCorpus of all products generated
   * @throws IOException
   * @throws ReactionException
   */

  private final List<Chemical> chemicalsOfInterest;
  private final List<Chemical> metabolites;
  private final List<Ero> roList;
  private final PredictionGenerator generator;

  public TwoSubstrateRoExpander(List<Chemical> chemicalsOfInterest,
                                List<Chemical> metabolites,
                                List<Ero> roList,
                                PredictionGenerator generator) {
    this.chemicalsOfInterest = chemicalsOfInterest;
    this.metabolites = metabolites;
    this.roList = roList;
    this.generator = generator;
  }

  public L2PredictionCorpus getPredictions() {

    List<Ero> listOfRos = getTwoSubstrateReactions(roList);

    LOGGER.info("The number of ROs to apply are %d", listOfRos.size());

    LOGGER.info("Constructing ro to molecule structures for metabolite list and chemicals of interest list.");
    Map<Integer, Set<Molecule>> roIdToMoleculesOfInterest = constructRoToMolecules(chemicalsOfInterest);
    Map<Integer, Set<Molecule>> roIdToMetabolites = constructRoToMolecules(metabolites);

    LOGGER.info("Perform L2 expansion for each ro in the list");
    L2PredictionCorpus result = new L2PredictionCorpus();
    int roProcessedCounter = 0;

    int predictionStartId = 0;
    for (Ero ro : listOfRos) {

      roProcessedCounter++;
      LOGGER.info("Processing the %d indexed ro out of %s ros", roProcessedCounter, listOfRos.size());

      // TODO: We only compute combinations of chemical of interest and metabolites, while not doing exclusive pairwise
      // comparisons of ONLY chemicals of interest or only metabolites. We dont compute pairwise metabolites operations
      // since the output of that dataset is not interesting (the cell should be making those already). However,
      // pairwise operations of chemicals of interest might be interesting edge cases ie ro takes in two of the same
      // molecules and outputs something novel. We do not do that here since it would add to the already long time
      // this function takes to execute.
      Set<Molecule> roMetabolitesSet = roIdToMetabolites.get(ro.getId());
      Set<Molecule> roMoleculesOfInterestSet = roIdToMoleculesOfInterest.get(ro.getId());

      if (roMetabolitesSet == null || roMoleculesOfInterestSet == null) {
        continue;
      }

      for (Molecule metabolite : roMetabolitesSet) {
        for (Molecule chemical : roMoleculesOfInterestSet) {

          List<Molecule> substrates = Arrays.asList(metabolite, chemical);

          try {
            result.addAll(generator.getPredictions(substrates, ro, NO_SAR));
            // If there is an error on a certain (RO, metabolite, chemical) set, we should log the error,
            // but the the expansion may produce some valid results, so no error is thrown.
          } catch (ReactionException e) {
            LOGGER.error("ReactionException On ro %d.", ro.getId(), e.getMessage());
          } catch (IOException e) {
            LOGGER.error("IOException On ro %d.", ro.getId(), e.getMessage());
          }
        }
      }
    }
    return result;
  }

  /**
   * This function constructs a ro to set of molecules map
   *
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
   * Filters the RO list to get rid of ROs with more or less than n substrates.
   *
   * @param roList The initial list of Ros.
   * @return The subset of the ros which have exactly n substrates.
   */
  private List<Ero> getTwoSubstrateReactions(List<Ero> roList) {
    List<Ero> twoSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == TWO_SUBSTRATES) {
        twoSubstrateReactions.add(ro);
      }
    }

    LOGGER.info("Proceeding with %d two-substrate ROs.", twoSubstrateReactions.size());
    return twoSubstrateReactions;
  }
}
