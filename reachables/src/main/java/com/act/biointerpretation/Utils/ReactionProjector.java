package com.act.biointerpretation.Utils;

import chemaxon.reaction.ConcurrentReactorProcessor;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.util.iterator.MoleculeIterator;
import chemaxon.util.iterator.MoleculeIteratorFactory;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeFormat$;
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
  private static final String MOL_NOT_FOUND = "NOT_FOUND";

  private static final MolSearchOptions LAX_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  private static final MolSearch DEFAULT_SEARCHER = new MolSearch();

  private static final String DEFAULT_MOLECULE_FORMAT = MoleculeFormat.noAuxInchi().toString();
  private final String moleculeFormat;

  /**
   * Set searcher to ignore stereo and bond type.  This will allow us to optimistically match products so that we don't
   * end up throwing out a reactor that produces the right compound in a slightly different form.
   */
  static {
    LAX_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE);
    LAX_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
    DEFAULT_SEARCHER.setSearchOptions(LAX_SEARCH_OPTIONS);
  }

  private MolSearch searcher;

  public ReactionProjector() {
    this(DEFAULT_SEARCHER, DEFAULT_MOLECULE_FORMAT);
  }

  public ReactionProjector(MolSearch searcher) {
    this(searcher, DEFAULT_MOLECULE_FORMAT);
  }

  public ReactionProjector(String moleculeFormat) {
    this(DEFAULT_SEARCHER, moleculeFormat);
  }

  public ReactionProjector(MolSearch searcher, String moleculeFormat) {
    this.searcher = searcher;
    this.moleculeFormat = moleculeFormat;
  }


  /**
   * This method should be called if projecting on more chemicals than should be stored in memory
   * simultaneously.  Until this is called, all chemicals acted on by the projector will be cached along
   * with their inchis.
   */
  public void clearInchiCache() {
    MoleculeExporter.clearCache();
  }

  /**
   * Run the given reactor until it produces the expected product. Reactor must produce one product at a time.
   *
   * @param reactor The reactor to run.
   * @param expectedProduct The product we expect to see.
   * @return The produced product; this is necessary because the reactor will produce the product with atom maps
   * corresponding to the substrate, whereas the expectedProduct Molecule will not have such atom maps.
   * @throws ReactionException If the expected product is not produced at all by the Reactor.
   * @throws IOException
   */
  public Molecule reactUntilProducesProduct(Reactor reactor, Molecule expectedProduct)
      throws ReactionException {
    if (reactor.getProductCount() != 1) {
      throw new IllegalArgumentException("Reactor must produce exactly one product.");
    }

    Molecule[] products;
    while ((products = reactor.react()) != null) {
      if (testEquality(products[0], expectedProduct)) {
        return products[0];
      }
    }

    throw new ReactionException("Expected product not among Reactor's predictions.");
  }

  /**
   * Tests equality of molecules based on two substructure queries. This is desirable because it makes the
   * equality comparison very configurable as compared to comparing inchis.
   *
   * @param A One molecule.
   * @param B Another molecule.
   * @return True if they are equivalent.
   */
  private boolean testEquality(Molecule A, Molecule B) {
    return substructureTest(A, B) && substructureTest(B, A);
  }

  private boolean substructureTest(Molecule substructure, Molecule superstructure) {
    searcher.setQuery(substructure);
    searcher.setTarget(superstructure);
    try {
      return searcher.findFirst() != null;
    } catch (SearchException e) {
      // Log error but don't propagate upward. Have never seen this before.
      LOGGER.error("Error on ReactionProjector.substructureTest(), %s", e.getMessage());
      return false;
    }
  }

  /**
   * Get the results of a reaction in list form, rather than as a map from substrates to products.
   *
   * @param mols The substrates.
   * @param reactor The reactor.
   * @return A list of product sets produced by this reaction.
   * @throws IOException
   * @throws ReactionException
   */
  public List<Molecule[]> getAllProjectedProductSets(Molecule[] mols, Reactor reactor)
      throws IOException, ReactionException {
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
   * Note that, for efficient two substrate expansion, the specialized method
   * fastProjectionOfTwoSubstrateRoOntoTwoMolecules should be used instead of this one.
   *
   * @param mols An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The substrate -> product map.
   */
  public Map<Molecule[], List<Molecule[]>> getRoProjectionMap(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {

    Map<Molecule[], List<Molecule[]>> resultsMap = new HashMap<>();

    if (mols.length != reactor.getReactantCount()) {
      LOGGER.debug("Tried to project RO with %d substrates on set of %d substrates",
          reactor.getReactantCount(),
          mols.length);
      return resultsMap;
    }

    // If there is only one reactant, we can do just a simple reaction computation. However, if we have multiple reactants,
    // we have to use the ConcurrentReactorProcessor API since it gives us the ability to combinatorially explore all
    // possible matching combinations of reactants on the substrates of the RO.
    if (mols.length == 1) {
      List<Molecule[]> productSets = getProductsFixedOrder(reactor, mols);
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

        Bag<Molecule> thisReactantSet = new HashBag<>(Arrays.asList(reactants));
        if (!originalReactantsSet.equals(thisReactantSet)) {
          LOGGER.debug("Reactant set %d does not represent original, complete reactant sets, skipping",
              reactantCombination);
          continue;
        }

        String thisHash = getStringHash(reactants);
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
   * @param mols Substrate molecules
   * @param reactor The two substrate reactor
   * @return A list of product arrays, where each array represents the products of a given reaction combination.
   * @throws ReactionException
   * @throws IOException
   */
  public Map<Molecule[], List<Molecule[]>> fastProjectionOfTwoSubstrateRoOntoTwoMolecules(Molecule[] mols, Reactor reactor)
      throws ReactionException, IOException {
    Map<Molecule[], List<Molecule[]>> results = new HashMap<>();

    Molecule[] firstCombinationOfSubstrates = new Molecule[] {mols[0], mols[1]};
    List<Molecule[]> productSets = getProductsFixedOrder(reactor, firstCombinationOfSubstrates);
    if (!productSets.isEmpty()) {
      results.put(firstCombinationOfSubstrates, productSets);
    }

    // Second ordering is same if two molecules are equal.
    if (getMoleculeString(mols[0]).equals(getMoleculeString(mols[1]))) {
      return results;
    }

    Molecule[] secondCombinationOfSubstrates = new Molecule[] {mols[1], mols[0]};
    productSets = getProductsFixedOrder(reactor, firstCombinationOfSubstrates);
    if (!productSets.isEmpty()) {
      results.put(secondCombinationOfSubstrates, productSets);
    }

    return results;
  }

  /**
   * Gets all product sets that a Reactor produces when applies to a given array of substrates, in only the order
   * that they are already in.
   *
   * @param reactor The Reactor.
   * @param substrates The substrates.
   * @return A list of product arrays returned by the Reactor.
   * @throws ReactionException
   */
  public List<Molecule[]> getProductsFixedOrder(Reactor reactor, Molecule[] substrates)
      throws ReactionException {

    reactor.setReactants(substrates);
    List<Molecule[]> results = new ArrayList<>();

    Molecule[] products;
    while ((products = reactor.react()) != null) {
      results.add(products);
    }

    return results;
  }

  /**
   * Builds a string which will be identical for two molecule arrays which represent the same molecules
   * in the same order, and different otherwise.
   *
   * @param mols The array of molecules.
   * @return The string representation.
   * @throws IOException
   */
  private String getStringHash(Molecule[] mols) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (Molecule molecule : mols) {
      builder.append(getMoleculeString(molecule));
    }
    return builder.toString();
  }

  /**
   * Gets a a string of the molecule.  The exporter takes care of all caching so we don't need to worry about it
   *
   * @param molecule The molecule.
   * @return The molecule's string presentation in the format that this class declares..
   * @throws IOException
   */
  private String getMoleculeString(Molecule molecule) throws IOException {
    return MoleculeExporter.exportMolecule(molecule, MoleculeFormat$.MODULE$.withName(this.moleculeFormat));
  }
}
