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
import java.util.Collections;
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

  // This value should always store the smallest value that has not thus far been used as atom map value.
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
      predictedProduct = projector.reactUntilProducesProduct(seedReactor, expectedProduct);
    } catch (ReactionException e) {
      LOGGER.warn("Validation RO doesn't take substrate to expectedProduct: %s", e.getMessage());
      throw e;
    }
    // convertToFrags() destroys the molecule, so this cloning is necessary. The clone method is from chemaxon and
    // produces a proper, deep copy of the molecule.
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
          if (fullReactor != null) {
            return fullReactor;
          }
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
   * Gets the expanded reactor corresponding to a particular search hit against a substructure fragment. Returns
   * null if no valid expansion is found.
   *
   * @param hit The SearchHit.
   * @return The Reactor.
   * @throws ReactionException
   */
  private Reactor getExpandedReaction(SearchHit hit) throws ReactionException {
    Set<Integer> relevantAtomMaps = getRelevantSubstrateAtomMaps(substrate, hit, seedReactor);

    if (relevantAtomMaps.isEmpty()) {
      return null;
    }

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
   * if there is overlap between the two sets. Return an empty set if there is no overlap, as this indicates that the
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

    return Collections.emptySet();
  }

  /**
   * Get the atom map values of atoms in the substrate that are involved in the reaction. This corresponds to all
   * atoms which are explicitly encoded in the reactor. In practice, to extract these, we must first extract the
   * atoms that correspond to product atoms included in the Reactor, and then add on "virgin" substrate atoms that
   * disappear in the transformation from substrate to product.
   *
   * @param substrate The substrate.
   * @param reactor The seedReactor.
   * @return The set of atom maps that the seedReactor acts on.
   */
  private Set<Integer> getSubstrateRoAtomMaps(Molecule substrate, Reactor reactor) {
    Set<Integer> roAtomMaps = new HashSet<>();
    Set<Integer> substrateIndicesInProduct = new HashSet<>();

    /**
     * The reactionMap is a map whose keys are MolAtoms in the product, and whose values are AtomIdentifiers that tell
     * you about how those atoms relate to the reaction that produced the product.
     *
     * In this loop, we add all substrate atoms that are explicitly marked as active by the reactionMap.
     */
    Map<MolAtom, AtomIdentifier> reactionMap = reactor.getReactionMap();
    for (AtomIdentifier id : reactionMap.values()) { // Iterate over the AtomIdentifier values of the ReactionMap.
      // If this AtomIdentifier is an orphan, this product atom does not correspond to an atom of the substrate.
      if (id.isOrphanAtom()) {
        continue;
      }
      // At this point, we know we have an AtomIdentifier that corresponds to an atom that is in both the substrate
      // and the product, but we don't know if it's involved in the reaction, or just untouched by it.

      // Keep tabs on the corresponding atom for later, but don't yet conclude that it's relevant.
      Integer substrateAtomIndex = id.getAtomIndex(); // Index of the corresponding atom in the substrate's atom array
      substrateIndicesInProduct.add(substrateAtomIndex); // Save for later.

      // The reactionSchemaMap has to do with how the Reactor internally keeps track of the mapping between
      // substrate and product atoms, I believe these values are only a property of the Reactor, not of these particular
      // substrates and products. The key is that, to tell if a product atom is explicitly included in the Reactor, we
      // can simply test if its associated reactionSchemaMap value is nonzero. If it is, then the reactor does
      // explicitly encode that atom.
      if (id.getReactionSchemaMap() > 0) {
        roAtomMaps.add(substrate.getAtomArray()[substrateAtomIndex].getAtomMap());
      }
    }

    /**
     * Now add in all substrate atoms which occur nowhere in the product: these are also implicitly acted upon.
     * In theory we should be able to find these atoms with the built-in isVirgin() method, but I didn't figure how to
     * get that to work. So instead, we iterate over all substrate atoms and filter out those we already saw correspoded
     * to product atoms.
     *
     * How I thought this could work with isVirginAtom is:
     * if (atomIdentifier.isVirginAtom(new Molecule[]{predictedProduct})) { add corresponding atom map to set }
     * But this throws indexOutOfBoundsExceptions within the isVirginAtom call.
     */
    for (int i = 0; i < substrate.getAtomArray().length; i++) { // Iterate over indices in the substrate atom array.
      if (!substrateIndicesInProduct.contains(i)) { // Test if this atom was NOT seen in the product
        roAtomMaps.add(substrate.getAtomArray()[i].getAtomMap()); // If it was not, add its atom map to the result set.
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
