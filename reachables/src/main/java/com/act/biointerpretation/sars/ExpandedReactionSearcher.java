package com.act.biointerpretation.sars;

import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.reaction.AtomIdentifier;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.sss.search.SearchHit;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ExpandedReactionSearcher {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ExpandedReactionSearcher.class);

  private static final MolSearchOptions LAX_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  private static final MolSearch DEFAULT_SEARCHER = new MolSearch();

  static {
    LAX_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE);
    LAX_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
    DEFAULT_SEARCHER.setSearchOptions(LAX_SEARCH_OPTIONS);
  }

  private static final Hydrogenize HYDROGENIZER = new Hydrogenize();

  private final ReactionProjector projector;
  private final MolSearch searcher;

  private int nextLabel;

  private Reactor seedReactor;
  private Molecule substrate;
  private Molecule expectedProduct;
  private Molecule substructure;

  private Molecule predictedProduct;
  private Iterator<Molecule> fragmentPointer;
  private Molecule currentFrag;
  private SearchHit currentHit;

  public ExpandedReactionSearcher(ReactionProjector projector, MolSearch searcher) {
    this.projector = projector;
    this.searcher = searcher;
  }

  public ExpandedReactionSearcher(ReactionProjector projector) {
    this.projector = projector;
    this.searcher = DEFAULT_SEARCHER;
  }

  /**
   * Initializes the searcher for a search by projecting the given Reactor on its substrate until it produces
   * the correct product, and resetting the fields for tracking the progress of the iterator. Must be called
   * before calling getNextReactor
   *
   * @param seed The seed Reactor.
   * @param sub The substrate.
   * @param prod The expected product.
   * @param substruct The MCS, from a set of reactions containing this one.
   * @throws ReactionException
   * @throws SearchException
   */
  public void initSearch(Reactor seed, Molecule sub, Molecule prod, Molecule substruct) throws ReactionException, SearchException {
    this.seedReactor = seed;
    this.substrate = sub;
    this.expectedProduct = prod;
    this.substructure = substruct;
    // Ensure that the resulting Reactor will include explicit hydrogens from RO
    HYDROGENIZER.convertImplicitHToExplicit(substrate);
    // Label the molecules of the substrate so we can correspond them to product and substructure molecules
    nextLabel = 1;
    labelMolecule(substrate);
    // After the next section, predictedProduct is an equivalent molecule to expectedProduct, but its atom map labels
    // match those of the substrate, which facilitates later computations.
    try {
      seedReactor.setReactants(new Molecule[] {substrate});
    } catch (ReactionException e) {
      LOGGER.info("Failed to setReactants. %s", e.getMessage());
      throw e;
    }
    try {
      predictedProduct = projector.runTillProducesProduct(seedReactor, expectedProduct);
    } catch (ReactionException e) {
      LOGGER.warn("Validation RO doesn't take substrate to expectedProduct: %s", e.getMessage());
      throw e;
    }
    // convertToFrags() destroys the molecule, so this cloning is necessary.
    Molecule substructureCopy = substructure.clone();
    fragmentPointer = Arrays.asList(substructureCopy.convertToFrags()).iterator();
    getNextFrag();
    getNextSearchHit();
  }

  /**
   * Gets the next possible expansion of the seed reactor according to the given substructure.
   * Returns null if there are no more possible reactors.
   *
   * @return The Reactor.
   */
  public Reactor getNextReactor() {

    while (currentFrag != null) {
      while (currentHit != null) {
        try {
          Reactor fullReactor = getExpandedReaction(currentHit);
          getNextSearchHit();
          return fullReactor;
        } catch (ReactionException e) {
        }
        getNextSearchHit();
      }
      getNextFrag();
    }

    return null;
  }

  /**
   * Get the next fragment of the substructure, and initialize the MolSearch searcher to look for that
   * fragment in the substrate. Modifies the instance variable currentFrag to its new value if successful,
   * or to null if unsuccessful.
   */
  private void getNextFrag() {
    if (!fragmentPointer.hasNext()) {
      currentFrag = null;
      return;
    }
    currentFrag = fragmentPointer.next();
    searcher.setQuery(currentFrag);
    searcher.setTarget(substrate);
  }

  /**
   * Get the next search hit from the current searcher.  Modifies the instance variable currentHIt to its new value
   * if successful, or to null if unsuccessful.
   */
  private void getNextSearchHit() {
    try {
      currentHit = searcher.findNextHit();
    } catch (SearchException e) {
      currentHit = null;
    }
  }

  /**
   * Gets the expanded reactior corresponding to a particular search hit against a substructure fragment.
   *
   * @param hit The SearchHit.
   * @return The Reactor.
   * @throws ReactionException If no reaction is generated.
   */
  private Reactor getExpandedReaction(SearchHit hit) throws ReactionException {
    Set<Integer> relevantAtomMaps = getRelevantSubstrateAtomMaps(substrate, hit, seedReactor);
    relevantAtomMaps.addAll(labelNewAtomsAndReturnAtomMaps(predictedProduct));

    // Copy molecules and then destructively remove portions that don't match the RO or substructure
    Molecule substrateCopy = substrate.clone();
    Molecule productCopy = predictedProduct.clone();
    retainRelevantAtoms(substrateCopy, relevantAtomMaps);
    retainRelevantAtoms(productCopy, relevantAtomMaps);

    try {
      return getFullReactor(substrateCopy, productCopy);
    } catch (ReactionException e) {
      LOGGER.warn("Failed to getFullReactor from final substrate and expectedProduct. %s", e.getMessage());
      throw e;
    }
  }

  /**
   * Label the given molecule with new atom map label values.
   *
   * @param mol The molecule to label.
   */
  private void labelMolecule(Molecule mol) {
    for (MolAtom atom : mol.getAtomArray()) {
      if (atom.getAtomMap() == 0) {
        atom.setAtomMap(nextLabel);
        nextLabel++;
      }
    }
  }

  /**
   * Get the atom maps of the substrate that are either in the substructure or affected by the seedReactor,
   * if there is overlap between the two sets. Throw an exception if there is no overlap, as this indicates that the
   * given search hit does not indicate any further constraint on where the RO can be applied.
   *
   * @param substrate The substrate of the reaction.
   * @param hit The search hit of the substructure in the substrate.
   * @param seedReactor The seed seedReactor from the validation corpus.
   * @return A full seedReactor incorporating the substructure and seed seedReactor, if one can be constructed.
   * @throws SearchException
   * @throws ReactionException
   */
  private Set<Integer> getRelevantSubstrateAtomMaps(Molecule substrate, SearchHit hit, Reactor seedReactor)
      throws ReactionException {
    Set<Integer> roAtomMaps = getSubstrateRoAtomMaps(substrate, seedReactor);
    Set<Integer> sarAtomMaps = getSarAtomMaps(substrate, hit);

    Set<Integer> overlap = new HashSet<>(sarAtomMaps);
    overlap.retainAll(roAtomMaps);

    // If the overlap is empty we don't want to build an extended RO- this indicates RO and SAR affect different
    // parts of the molecule.
    if (!overlap.isEmpty()) {
      roAtomMaps.addAll(sarAtomMaps);
      return roAtomMaps;
    }

    throw new ReactionException("RO does not overlap this substructure fragment.");
  }

  /**
   * Get the atom map values of atoms in the substrate that are involved in the reaction.
   *
   * @param substrate The substrate.
   * @param reactor The seedReactor.
   * @return The set of atom maps that the seedReactor acts on.
   */
  private Set<Integer> getSubstrateRoAtomMaps(Molecule substrate, Reactor reactor) {
    Set<Integer> roAtomMaps = new HashSet<>();
    Set<Integer> substrateIndicesInProduct = new HashSet<>();

    // Add all substrate atoms that are explicitly marked as active by the reactionMap.
    // The maps added in this block all correspond to atoms in the product, since the map keys are product atoms.
    Map<MolAtom, AtomIdentifier> reactionMap = reactor.getReactionMap();

    for (MolAtom atom : reactionMap.keySet()) { // Iterate over product atoms in the map
      AtomIdentifier id = reactionMap.get(atom);
      Integer substrateAtomIndex = id.getAtomIndex(); // Index of the corresponding atom in the substrate's atom array

      if (substrateAtomIndex >= 0) {
        substrateIndicesInProduct.add(substrateAtomIndex); // Keep tabs on all substrate atoms that occur in product.
        boolean productAtomActiveInRo = id.getReactionSchemaMap() > 0;

        if (productAtomActiveInRo) {
          roAtomMaps.add(substrate.getAtomArray()[substrateAtomIndex].getAtomMap());
        }
      }
    }

    // Now add in all substrate atoms which occur nowhere in the product: these are also implicitly acted upon.
    for (int i = 0; i < substrate.getAtomArray().length; i++) {
      if (!substrateIndicesInProduct.contains(i)) {
        roAtomMaps.add(substrate.getAtomArray()[i].getAtomMap());
      }
    }

    return roAtomMaps;
  }

  /**
   * Returns the atom map values corresponding to a given substructure hit.
   *
   * @param substrate The substrate.
   * @param hit The search hit in the substrate.
   * @return The substrate atom maps that match the next search hit.
   * @throws SearchException
   */
  private Set<Integer> getSarAtomMaps(Molecule substrate, SearchHit hit) {
    Set<Integer> sarAtomMaps = new HashSet<>();
    for (Integer atomId : hit.getSingleHit()) { // The values in the hit are indices into the substrate's atom array
      sarAtomMaps.add(substrate.getAtomArray()[atomId].getAtomMap());
    }
    return sarAtomMaps;
  }

  /**
   * Label every zero-labeled atom in the molecule with a new label, and return the newly labeled atoms' map values.
   *
   * @param product The molecule to label.
   * @return The atom maps of the labeled atoms.
   */
  private Set<Integer> labelNewAtomsAndReturnAtomMaps(Molecule product) {
    Set<Integer> result = new HashSet<>();
    for (MolAtom atom : product.getAtomArray()) {
      if (atom.getAtomMap() == 0) { // Atoms are zero-labeled only if absent in substrate - these are important!
        atom.setAtomMap(nextLabel);
        result.add(nextLabel);
        nextLabel++;
      }
    }
    return result;
  }

  /**
   * Remove all atoms that don't have atom maps in the relevantAtoms set from the molecule
   *
   * @param molecule The molecule to trim.
   * @param relevantAtoms The atom map values to keep.
   */
  private void retainRelevantAtoms(Molecule molecule, Set<Integer> relevantAtoms) {
    for (MolAtom atom : molecule.getAtomArray()) {
      if (!relevantAtoms.contains(atom.getAtomMap())) {
        molecule.removeAtom(atom);
      }
    }
  }

  /**
   * Build a seedReactor that takes the given substrate Molecules to the given Product molecule.
   *
   * @param finalSubstrate The substrate.
   * @param finalProduct The expectedProduct.
   * @return The Reactor.
   * @throws ReactionException If the reactor could not be built.
   */
  private Reactor getFullReactor(Molecule finalSubstrate, Molecule finalProduct) throws ReactionException {
    RxnMolecule rxnMolecule = new RxnMolecule();
    rxnMolecule.addComponent(finalSubstrate, RxnMolecule.REACTANTS);
    rxnMolecule.addComponent(finalProduct, RxnMolecule.PRODUCTS);

    Reactor fullReactor = new Reactor();
    fullReactor.setReaction(rxnMolecule);
    return fullReactor;
  }

}
