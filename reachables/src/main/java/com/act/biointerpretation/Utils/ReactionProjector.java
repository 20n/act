package com.act.biointerpretation.Utils;

import chemaxon.marvin.io.MolExportException;
import chemaxon.reaction.ConcurrentReactorProcessor;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import chemaxon.util.iterator.MoleculeIterator;
import chemaxon.util.iterator.MoleculeIteratorFactory;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeFormat$;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.iterators.PermutationIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ReactionProjector {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionProjector.class);
  private static final String MOL_NOT_FOUND = "NOT_FOUND";

  private static final MolSearchOptions LAX_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  private static final MolSearch DEFAULT_SEARCHER = new MolSearch();

  private static final String DEFAULT_MOLECULE_FORMAT = MoleculeFormat.noAuxInchi().value().toString();
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

  private transient ThreadLocal<MolSearch> substrateSearcher = new ThreadLocal<MolSearch>() {
    @Override
    protected MolSearch initialValue() {
      MolSearch search = new MolSearch();
      /* These parameters were identified by trial and error as reasonable defaults for doing substructure searches for
       * reactor substrate matching.  */
      MolSearchOptions options = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
      // This allows H's in RO strings to match implicit hydrogens in our target molecules.
      options.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_ENABLED);
      /* This allows for vague bond matching in ring structures.  From the Chemaxon Docs:
       *    In the query all single ring bonds are replaced by "single or aromatic" and all double ring bonds are
       *    replaced by "double or aromatic" prior to search.
       *    (https://www.chemaxon.com/jchem/doc/dev/java/api/chemaxon/sss/SearchConstants.html)
       *
       * This should allow us to handle aromatized molecules gracefully without handling non-ring single and double
       * bonds ambiguously. */
      options.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2);
      // Few if any of our ROs concern stereo chemistry, so we can just ignore it.
      options.setStereoSearchType(SearchConstants.STEREO_IGNORE);
      /* Chemaxon's tautomer handling is weird, as sometimes it picks a non-representative tautomer as its default.
       * As such, we'll allow tautomer matches to avoid excluding viable candidates. */
      options.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON);
      search.setSearchOptions(options);
      return search;
    }
  };

  private boolean useSubstructureFiltering = false;

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

  public ReactionProjector(boolean useSubstructureFiltering) {
    // I don't anticipate either of these ever being used if this is the constructor employed.
    this.searcher = DEFAULT_SEARCHER;
    this.moleculeFormat = DEFAULT_MOLECULE_FORMAT;
    this.useSubstructureFiltering = useSubstructureFiltering;
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
   * @param reactor         The reactor to run.
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
   * @param mols    The substrates.
   * @param reactor The reactor.
   * @param maxProducts The maximum number of products to compute before exiting.  No limit is imposed for max values
   *                    of null or 0.
   * @return A list of product sets produced by this reaction.
   * @throws IOException
   * @throws ReactionException
   */
  public List<Molecule[]> getAllProjectedProductSets(Molecule[] mols, Reactor reactor, Integer maxProducts)
      throws IOException, ReactionException {
    Map<Molecule[], List<Molecule[]>> map = getRoProjectionMap(mols, reactor, maxProducts);

    List<Molecule[]> allProductSets = new ArrayList<>();

    for (Map.Entry<Molecule[], List<Molecule[]>> entry : map.entrySet()) {
      allProductSets.addAll(entry.getValue());
    }

    return allProductSets;
  }

  public List<Molecule[]> getAllProjectedProductSets(Molecule[] mols, Reactor reactor)
      throws IOException, ReactionException {
    return getAllProjectedProductSets(mols, reactor, null);
  }

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the products of the transformation.
   * The results are returned as a map from orderings of the substrates to the products produced by those orderings.
   * In most cases the map will have only one entry, but in some cases different orderings of substrates can lead to
   * different valid predictions.
   * <p>
   * Note that, for efficient two substrate expansion, the specialized method
   * fastProjectionOfTwoSubstrateRoOntoTwoMolecules should be used instead of this one.
   *
   * @param mols    An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @param maxProducts The maximum number of products to compute before exiting.  No limit is imposed for max values
   *                    of null or 0.
   * @return The substrate -> product map.
   */
  public Map<Molecule[], List<Molecule[]>> getRoProjectionMap(Molecule[] mols, Reactor reactor, Integer maxProducts)
      throws ReactionException, IOException {

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
      List<Molecule[]> productSets = getProductsFixedOrder(reactor, mols, maxProducts);
      if (!productSets.isEmpty()) {
        resultsMap.put(mols, productSets);
      }
    } else if (useSubstructureFiltering) {
      Optional<List<Set<Integer>>> viableSubstrateIndexes;
      try {
        viableSubstrateIndexes = matchCandidatesToSubstrateStructures(reactor, mols);
      } catch (SearchException e) {
        throw new ReactionException("Caught exception when performing pre-reaction substructure search", e);
      }

      if (viableSubstrateIndexes.isPresent()) {
        List<Integer> allIndexes = new ArrayList<Integer>(mols.length) {{
          for (int i = 0; i < mols.length; i++) {
            add(i);
          }
        }};

        int permutationIndex = 0;
        PermutationIterator<Integer> iter = new PermutationIterator<>(allIndexes);
        while (iter.hasNext()) {
          permutationIndex++;
          LOGGER.info("Running permutation %d", permutationIndex);
          List<Integer> permutation = iter.next();
          if (permutationFitsSubstructureMatches(permutation, viableSubstrateIndexes.get())) {
            Molecule[] substrates = indexPermutationToMolecules(mols, permutation);
            reactor.setReactants(substrates);
            Molecule[] products;
            List<Molecule[]> results = new ArrayList<>();
            while ((products = reactor.react()) != null) {
              results.add(products);
              if (maxProducts != null && maxProducts > 0 && results.size() >= maxProducts) {
                break;
              }
            }
            if (results.size() > 0) {
              resultsMap.put(substrates, results);
            }
          }
        }
      } // Otherwise just return the empty map.
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
        if (maxProducts != null && maxProducts > 0 && resultsMap.size() >= maxProducts) {
          break;
        }
      }
    }
    return resultsMap;
  }

  private Optional<List<Set<Integer>>> matchCandidatesToSubstrateStructures(Reactor reactor, Molecule[] candidates)
      throws SearchException, MolExportException {

    RxnMolecule rxnMol = reactor.getReaction();
    Molecule[] substrateStructures = rxnMol.getReactants();

    MolSearch search = substrateSearcher.get();

    /* Each list in `results` is a set of viable indexes into candidates for a given substrate position in the reactor.
     * So if candidates contains three molecules and results looks like:
     *   0: 0, 1
     *   1: 1
     *   2: 0, 2
     *
     * Then candidates[0] can appear as substrate 0 or 2, candidates[1] as substrate 0 or 1, and candidates[2] only as
     * substrate 2. */
    List<Set<Integer>> results = new ArrayList<>(substrateStructures.length);

    for (int i = 0; i < substrateStructures.length; i++) {
      search.setQuery(substrateStructures[i]);
      results.add(new HashSet<>());

      /* Test each candidate molecule against each substrate structure of the reactor.  If we can't find a match,
       * there's no way that the molecule could appear in a particular position in the reaction and we can later
       * eliminate input molecule permutations that don't conform to this structure match. */
      for (int j = 0; j < candidates.length; j++) {
        search.setTarget(candidates[j]);
        // Any match will do, so just call findFirst and ensure we get some sort of results.
        int[] res = search.findFirst();
        if (res != null) {
          results.get(i).add(j);
        }
      }

      /* If one of the substrate patterns can't be matched to any input structure, then there are no viable permutations
       * to consider.  We tell the call as much by returning `empty`. */
      if (results.get(i).size() == 0) {
        return Optional.empty();
      }
    }

    return Optional.of(results);
  }

  private Boolean permutationFitsSubstructureMatches(
      List<Integer> permutation, List<Set<Integer>> viableIndexes) {
      /* For each permutation, match its value at each location against the list of viable indexes for that location.
       * If a given permutation is 0, 2, 1 and viableIndexes looks like
       *   0: 0, 1
       *   1: 1
       *   2: 0, 2
       * as above, then then the second structure index doesn't pass a substructure search and we can skip it. */
      for (int i = 0; i < permutation.size(); i++) {
        if (!viableIndexes.get(i).contains(permutation.get(i))) {
          return false;
        }
      }
      return true;
  }

  private Molecule[] indexPermutationToMolecules(Molecule[] molecules, List<Integer> permutation) {
    Molecule[] results = new Molecule[molecules.length];
    for (int i = 0; i < permutation.size(); i++) {
      results[i] = molecules[permutation.get(i)];
    }
    return results;
  }

  public Map<Molecule[], List<Molecule[]>> getRoProjectionMap(Molecule[] mols, Reactor reactor)
      throws ReactionException, IOException {
    return getRoProjectionMap(mols, reactor, null);
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
  public Map<Molecule[], List<Molecule[]>> fastProjectionOfTwoSubstrateRoOntoTwoMolecules(Molecule[] mols, Reactor reactor)
      throws ReactionException, IOException {
    Map<Molecule[], List<Molecule[]>> results = new HashMap<>();

    Molecule[] firstCombinationOfSubstrates = new Molecule[]{mols[0], mols[1]};
    List<Molecule[]> productSets = getProductsFixedOrder(reactor, firstCombinationOfSubstrates);
    if (!productSets.isEmpty()) {
      results.put(firstCombinationOfSubstrates, productSets);
    }

    // Second ordering is same if two molecules are equal.
    if (getMoleculeString(mols[0]).equals(getMoleculeString(mols[1]))) {
      return results;
    }

    Molecule[] secondCombinationOfSubstrates = new Molecule[]{mols[1], mols[0]};
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
   * @param reactor    The Reactor.
   * @param substrates The substrates.
   * @param maxProducts The maximum number of products to compute before exiting.  No limit is imposed for max values
   *                    of null or 0.
   * @return A list of product arrays returned by the Reactor.
   * @throws ReactionException
   */
  public List<Molecule[]> getProductsFixedOrder(Reactor reactor, Molecule[] substrates, Integer maxProducts)
      throws ReactionException {

    reactor.setReactants(substrates);
    List<Molecule[]> results = new ArrayList<>();

    Molecule[] products;
    while ((products = reactor.react()) != null) {
      results.add(products);
      if (maxProducts != null && maxProducts > 0 && results.size() >= maxProducts) {
        break;
      }
    }

    return results;
  }

  public List<Molecule[]> getProductsFixedOrder(Reactor reactor, Molecule[] substrates)
      throws ReactionException {
    return getProductsFixedOrder(reactor, substrates, null);
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
    return MoleculeExporter.exportMolecule(molecule, MoleculeFormat$.MODULE$.getName(this.moleculeFormat));
  }
}
