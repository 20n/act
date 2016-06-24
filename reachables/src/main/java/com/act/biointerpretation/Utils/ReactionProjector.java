package com.act.biointerpretation.Utils;

import chemaxon.reaction.ConcurrentReactorProcessor;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.util.iterator.MoleculeIterator;
import chemaxon.util.iterator.MoleculeIteratorFactory;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReactionProjector {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionProjector.class);

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the product of the transformation.
   *
   * @param mols An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The product of the reaction
   */
  public static Molecule[] projectRoOnMolecules(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {
    // If there is only one reactant, we can do just a simple reaction computation. However, if we have multiple reactants,
    // we have to use the ConcurrentReactorProcessor API since it gives us the ability to combinatorially explore all
    // possible matching combinations of reactants on the substrates of the RO.
    if (mols.length == 1) {
      reactor.setReactants(mols);
      Molecule[] products = reactor.react();
      return products;
    } else {
      // TODO: why not make one of these per ReactionProjector object?
      ConcurrentReactorProcessor reactorProcessor = new ConcurrentReactorProcessor();
      reactorProcessor.setReactor(reactor);

      // This step is needed by ConcurrentReactorProcessor for the combinatorial exploration.
      // The iterator takes in the same substrates in each iteration step to build a matrix of combinations.
      // For example, if we have A + B -> C, the iterator creates this array: [[A,B], [A,B]]. Based on this,
      // ConcurrentReactorProcessor will cross the elements in the first index with the second, creating the combinations
      // A+A, A+B, B+A, B+B and operates all of those on the RO, which is what is desired.
      MoleculeIterator[] iterator = new MoleculeIterator[mols.length];
      for (int i = 0; i < mols.length; i++) {
        iterator[i] = MoleculeIteratorFactory.createMoleculeIterator(mols);
      }

      reactorProcessor.setReactantIterators(iterator, ConcurrentReactorProcessor.MODE_COMBINATORIAL);

      // Bag is a multi-set class that ships with Apache commons collection, and we already use many commons libs--easy!
      Bag<Molecule> originalReactantsSet = new HashBag<>(Arrays.asList(mols));
      List<Molecule[]> allResults = new ArrayList<>();

      List<Molecule[]> results = null;
      int reactantCombination = 0;
      while ((results = reactorProcessor.reactNext()) != null) {
        reactantCombination++;
        Molecule[] reactants = reactorProcessor.getReactants();

        if (results.size() == 0) {
          LOGGER.debug("No results found for reactants combination %d, skipping", reactantCombination);
          continue;
        }

        Bag<Molecule> thisReactantSet = new HashBag<>(Arrays.asList(reactants));
        if (!originalReactantsSet.equals(thisReactantSet)) {
          LOGGER.debug("Reactant set %d does not represent original, complete reactant sets, skipping",
              reactantCombination);
          continue;
        }

        if (results.size() != 0) {
          allResults.addAll(results);
        }
      }

      /* TODO: dropping other possible results on the floor isn't particularly appealing.  How can we better handle
       * reactions that produce ambiguous products? */
      if (allResults.size() > 1) {
        // It's unclear how best to handle truly ambiguous RO products, so log an error and return the first.
        LOGGER.error("RO projection returned multiple possible product sets, returning first");
        return allResults.get(0);
      } else if (allResults.size() == 1) {
        return allResults.get(0);
      }

      return null;
    }
  }

  public static Molecule[] fastProjectionOfRoOntoMolecules(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {



  }
}
