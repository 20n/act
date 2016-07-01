package com.act.biointerpretation.Utils;

import chemaxon.calculations.clean.Cleaner;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionProjector {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionProjector.class);
  private static final Integer TWO_DIMENSION = 2;

  private static Molecule[] filterAndReturnLegalMolecules(Molecule[] molecules) {
    List<Molecule> filteredMolecules = new ArrayList<>();

    for (Molecule molecule : molecules) {
      Cleaner.clean(molecule, TWO_DIMENSION);
      filteredMolecules.add(molecule);
    }

    return filteredMolecules.toArray(new Molecule[filteredMolecules.size()]);
  }

  public static List<Molecule[]> getAllProjectedProductSets(Molecule[] mols, Reactor reactor) throws IOException, ReactionException {
    Map<Molecule[], List<Molecule[]>> map = getRoProjectionMap(mols, reactor);

    List<Molecule[]> allProductSets = new ArrayList<>();

    for (Map.Entry<Molecule[], List<Molecule[]>> entry : map.entrySet()) {
      allProductSets.addAll(entry.getValue());
    }

    return allProductSets;
  }

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the product of the transformation.
   *
   * @param mols    An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The product of the reaction
   */
  public static Map<Molecule[], List<Molecule[]>> getRoProjectionMap(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {
    Map<Molecule[], List<Molecule[]>> resultsMap = new HashMap<>();

    // If there is only one reactant, we can do just a simple reaction computation. However, if we have multiple reactants,
    // we have to use the ConcurrentReactorProcessor API since it gives us the ability to combinatorially explore all
    // possible matching combinations of reactants on the substrates of the RO.
    if (mols.length == 1) {
      List<Molecule[]> productSets = getAllProductSets(reactor, mols);
      if (!productSets.isEmpty()) {
        resultsMap.put(mols, productSets);
      }
      return resultsMap;
    } else if (mols.length == 2) {
      return fastProjectionOfTwoSubstrateRoOntoTwoMolecules(mols, reactor);
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

        if (results.isEmpty()) {
          LOGGER.debug("No results found for reactants combination %d, skipping", reactantCombination);
          continue;
        }

        Bag<Molecule> thisReactantSet = new HashBag<>(Arrays.asList(reactants));
        if (!originalReactantsSet.equals(thisReactantSet)) {
          LOGGER.debug("Reactant set %d does not represent original, complete reactant sets, skipping",
              reactantCombination);
          continue;
        }

        resultsMap.put(reactants, results);
      }

      return resultsMap;
    }
  }

  /**
   * This function projects all possible combinations of two input substrates onto a 2 substrate RO, then
   * cleans and returns the results of that projection.
   * TODO: Expand this class to handle 3 or 4 substrate reactions.
   *
   * @param mols    Substrate molecules
   * @param reactor The two substrate reactor
   * @return A list of product arrays, where each array represents the products of a given reaction combination.
   * @throws ReactionException
   * @throws IOException
   */
  public static Map<Molecule[], List<Molecule[]>> fastProjectionOfTwoSubstrateRoOntoTwoMolecules(Molecule[] mols, Reactor reactor)
      throws ReactionException, IOException {
    Map<Molecule[], List<Molecule[]>> results = new HashMap<>();

    Molecule[] firstCombinationOfSubstrates = new Molecule[]{mols[0], mols[1]};
    List<Molecule[]> productSets = getAllProductSets(reactor, firstCombinationOfSubstrates);
    if (!productSets.isEmpty()) {
      results.put(firstCombinationOfSubstrates, getAllProductSets(reactor, firstCombinationOfSubstrates));
    }

    Molecule[] secondCombinationOfSubstrates = new Molecule[]{mols[1], mols[0]};
    productSets = getAllProductSets(reactor, firstCombinationOfSubstrates);
    if (!productSets.isEmpty()) {
      results.put(secondCombinationOfSubstrates, productSets);
    }

    return results;
  }

  private static List<Molecule[]> getAllProductSets(Reactor reactor, Molecule[] substrates) throws ReactionException {
    reactor.setReactants(substrates);
    List<Molecule[]> results = new ArrayList<>();

    Molecule[] products;
    while ((products = reactor.react()) != null) {
      results.add(filterAndReturnLegalMolecules(products));
    }

    return results;
  }
}
