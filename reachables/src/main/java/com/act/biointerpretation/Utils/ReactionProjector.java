package com.act.biointerpretation.Utils;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactionProjector {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionProjector.class);
  private static final Integer TWO_DIMENSION = 2;

  //TODO: move cleaning out of projector; let clients handle.
  private static Molecule[] filterAndReturnLegalMolecules(Molecule[] molecules) {
    List<Molecule> filteredMolecules = new ArrayList<>();

    for (Molecule molecule : molecules) {
      Cleaner.clean(molecule, TWO_DIMENSION);
      filteredMolecules.add(molecule);
    }

    return filteredMolecules.toArray(new Molecule[filteredMolecules.size()]);
  }

  /**
   * Get the results of a reaction in list form, rather than as a map from substrates to products.
   *
   * @param mols    The substrates.
   * @param reactor The reactor.
   * @return A list of product sets produced by this reaction.
   * @throws IOException
   * @throws ReactionException
   */
  public static List<Molecule[]> getAllProjectedProductSets(Molecule[] mols, Reactor reactor) throws IOException, ReactionException {
    Map<Molecule[], List<Molecule[]>> map = getRoProjectionMap(mols, reactor);

    List<Molecule[]> allProductSets = new ArrayList<>();

    for (Map.Entry<Molecule[], List<Molecule[]>> entry : map.entrySet()) {
      allProductSets.addAll(entry.getValue());
    }

    return allProductSets;
  }

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the products of the transformation.
   * The results are returned as a map from orderings of the substrates to the products produced by those orderings.
   * In most cases the map will have only one entry, but in some cases different orderings of substrates can lead to
   * different valid predictions.
   *
   * @param mols    An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The substrate -> product map.
   */
  public static Map<Molecule[], List<Molecule[]>> getRoProjectionMap(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {
    boolean cleanMolecules = false;
    Map<Molecule[], List<Molecule[]>> resultsMap = new HashMap<>();

    if (mols.length != reactor.getReactantCount()) {
      return resultsMap;
    }

    // If there is only one reactant, we can do just a simple reaction computation. However, if we have multiple reactants,
    // we have to use the ConcurrentReactorProcessor API since it gives us the ability to combinatorially explore all
    // possible matching combinations of reactants on the substrates of the RO.
    if (mols.length == 1) {
      List<Molecule[]> productSets = getAllProductSets(reactor, mols, cleanMolecules);
      if (!productSets.isEmpty()) {
        resultsMap.put(mols, productSets);
      }
      return resultsMap;
    } else {
      // TODO: why not make one of these per ReactionProjector object?
      // TODO: replace this with Apache commons PermutationIterator for clean iteration over distinct permutations.
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

      // This set keeps track of substrate combinations we've used, and avoids repeats.  Repeats can occur
      // when several substrates are identical, and can be put in "different" but symmetric orderings.
      Set<String> substrateHashes = new HashSet<>();

      List<Molecule[]> results = null;
      int reactantCombination = 0;
      while ((results = reactorProcessor.reactNext()) != null) {
        reactantCombination++;
        Molecule[] reactants = reactorProcessor.getReactants();

        if (results.isEmpty()) {
          LOGGER.debug("No results found for reactants combination %d, skipping", reactantCombination);
          continue;
        }

        String thisHash = getStringHash(reactants);
        Bag<Molecule> thisReactantSet = new HashBag<>(Arrays.asList(reactants));
        if (!originalReactantsSet.equals(thisReactantSet)) {
          LOGGER.debug("Reactant set %d does not represent original, complete reactant sets, skipping",
              reactantCombination);
          continue;
        }
        if (substrateHashes.contains(thisHash)) {
          continue;
        }

        substrateHashes.add(thisHash);
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
    boolean cleanMolecules = true;
    Map<Molecule[], List<Molecule[]>> results = new HashMap<>();

    Molecule[] firstCombinationOfSubstrates = new Molecule[]{mols[0], mols[1]};
    String firstHash = getStringHash(firstCombinationOfSubstrates);
    List<Molecule[]> productSets = getAllProductSets(reactor, firstCombinationOfSubstrates, cleanMolecules);
    if (!productSets.isEmpty()) {
      results.put(firstCombinationOfSubstrates, productSets);
    }

    Molecule[] secondCombinationOfSubstrates = new Molecule[]{mols[1], mols[0]};
    if (!getStringHash(secondCombinationOfSubstrates).equals(firstHash)) {
      productSets = getAllProductSets(reactor, firstCombinationOfSubstrates, cleanMolecules);
      if (!productSets.isEmpty()) {
        results.put(secondCombinationOfSubstrates, productSets);
      }
    }

    return results;
  }

  private static List<Molecule[]> getAllProductSets(Reactor reactor, Molecule[] substrates, boolean clean)
      throws ReactionException {

    reactor.setReactants(substrates);
    List<Molecule[]> results = new ArrayList<>();

    Molecule[] products;
    while ((products = reactor.react()) != null) {
      if (clean) {
        results.add(filterAndReturnLegalMolecules(products));
      } else {
        results.add(products);
      }
    }

    return results;
  }

  private static String getStringHash(Molecule[] mols) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (Molecule molecule : mols) {
      builder.append(MolExporter.exportToFormat(molecule, "inchi"));
    }
    return builder.toString();
  }
}
