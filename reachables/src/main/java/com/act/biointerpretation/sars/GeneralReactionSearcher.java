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

public class GeneralReactionSearcher {

  private static final Logger LOGGER = LogManager.getFormatterLogger(GeneralReactionSearcher.class);

  private static final MolSearchOptions LAX_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    LAX_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE);
    LAX_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }

  private static final Hydrogenize HYDROGENIZER = new Hydrogenize();

  private final ReactionProjector projector;

  private int nextLabel;

  private Reactor seedReactor;
  private Molecule substrate;
  private Molecule expectedProduct;
  private Molecule substructure;

  private Molecule predictedProduct;
  private Iterator<Molecule> fragmentPointer;

  public GeneralReactionSearcher(ReactionProjector projector) {
    this.projector = projector;
  }

  public void initSearch() throws ReactionException {
    nextLabel = 1;

    // Ensure that the resulting Reactor will include explicit hydrogens from RO
    HYDROGENIZER.convertImplicitHToExplicit(substrate);
    labelMolecule(substrate);
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

    Molecule substructureCopy = substructure.clone();
    fragmentPointer = Arrays.asList(substructureCopy.convertToFrags()).iterator();
  }

  public Reactor getNextGeneralization() {

    if (fragmentPointer == null) {
      throw new NullPointerException("Must initSearch before getting generalizations.");
    }

    Reactor fullReactor;
    while (fragmentPointer.hasNext()) {
      try {
        fullReactor = getReactionGeneralization(fragmentPointer.next());
      } catch (ReactionException e) {
        continue;
      }
      return fullReactor;
    }

    return null;
  }

  private Reactor getReactionGeneralization(Molecule fragment) throws ReactionException {
    Set<Integer> substrateAtomMaps = null;
    try {
      substrateAtomMaps = getRelevantAtomMaps(substrate, fragment, seedReactor);
    } catch (SearchException e) {
      throw new ReactionException("SearchException on getRelevantAtomMaps: " + e.getMessage());
    }

    if (substrateAtomMaps == null) {
      LOGGER.error("Didn't find substructure that overlapped RO.");
      return seedReactor;
    }

    Set<Integer> productAtomMaps = new HashSet(substrateAtomMaps);
    productAtomMaps.addAll(labelAndGetZeroLabeledAtoms(predictedProduct));

    Molecule substrateCopy = substrate.clone();
    Molecule productCopy = predictedProduct.clone();

    removeIrrelevantPortion(substrateCopy, substrateAtomMaps);
    removeIrrelevantPortion(productCopy, productAtomMaps);

    try {
      return getFullReactor(substrateCopy, productCopy);
    } catch (ReactionException e) {
      LOGGER.warn("Failed to getFullReactor from final substrate and expectedProduct. %s", e.getMessage());
      throw e;
    }
  }

  /**
   * Build a seedReactor that takes the given substrate Molecules to the given Product molecule.
   *
   * @param finalSubstrate The substrate.
   * @param finalProduct The expectedProduct.
   * @return The Reactor.
   * @throws ReactionException
   */
  private Reactor getFullReactor(Molecule finalSubstrate, Molecule finalProduct) throws ReactionException {
    RxnMolecule rxnMolecule = new RxnMolecule();
    rxnMolecule.addComponent(finalSubstrate, RxnMolecule.REACTANTS);
    rxnMolecule.addComponent(finalProduct, RxnMolecule.PRODUCTS);

    Reactor fullReactor = new Reactor();
    fullReactor.setReaction(rxnMolecule);
    return fullReactor;
  }

  /**
   * Remove all atoms that don't have atom maps in the relevantAtoms set from the molecule
   *
   * @param molecule The molecule to trim.
   * @param relevantAtoms The atom map values to keep.
   */
  private void removeIrrelevantPortion(Molecule molecule, Set<Integer> relevantAtoms) {
    for (MolAtom atom : molecule.getAtomArray()) {
      if (!relevantAtoms.contains(atom.getAtomMap())) {
        molecule.removeAtom(atom);
      }
    }
  }

  /**
   * Label every zero-labeled atom in a molecule with a new label, and return the newly labeled atoms.
   *
   * @param product The molecule to label.
   * @return The atom maps of the labeled atoms.
   */
  private Set<Integer> labelAndGetZeroLabeledAtoms(Molecule product) {
    Set<Integer> result = new HashSet<>();
    for (MolAtom atom : product.getAtomArray()) {
      if (atom.getAtomMap() == 0) {
        atom.setAtomMap(nextLabel);
        result.add(nextLabel);
        nextLabel++;
      }
    }
    return result;
  }

  /**
   * Get the atom maps of the substrate that are either in the substructure or affected by the seedReactor,
   * if there is overlap between the two sets. Throw an exception if there is no overlap.
   *
   * @param substrate The substrate of the reaction.
   * @param substructure The substructure molecule that is found in all substrates.
   * @param seedReactor The seed seedReactor from the validation corpus.
   * @return A full seedReactor incorporating the substructure and seed seedReactor, if one can be constructed.
   * @throws SearchException
   * @throws ReactionException
   */
  private Set<Integer> getRelevantAtomMaps(Molecule substrate, Molecule substructure, Reactor seedReactor)
      throws SearchException, ReactionException {
    Set<Integer> roAtomMaps = getRoAtomMaps(substrate, seedReactor);
    MolSearch searcher = getSearcher(substrate, substructure);
    Set<Integer> sarAtomMaps;

    while ((sarAtomMaps = getNextSarAtomMap(substrate, searcher)) != null) {
      Set<Integer> overlap = new HashSet<>(sarAtomMaps);
      overlap.retainAll(roAtomMaps);

      // If the overlap is empty we don't want to build an extended RO- this indicates RO and SAR affect different
      // parts of the molecule.
      if (!overlap.isEmpty()) {
        roAtomMaps.addAll(sarAtomMaps);
        return roAtomMaps;
      }
    }

    throw new ReactionException("RO does not overlap this substructure fragment.");
  }

  /**
   * Get the atom map values of the substrate that are encoded in the seedReactor.
   *
   * @param substrate The substrate.
   * @param reactor The seedReactor.
   * @return The set of atom maps that the seedReactor acts on.
   */
  private Set<Integer> getRoAtomMaps(Molecule substrate, Reactor reactor) {
    Set<Integer> roAtomMaps = new HashSet<>();
    Map<MolAtom, AtomIdentifier> reactionMap = reactor.getReactionMap();

    for (MolAtom atom : reactionMap.keySet()) {
      AtomIdentifier id = reactionMap.get(atom);
      if (id.getAtomIndex() > 0 && id.getReactionSchemaMap() > 0) {
        roAtomMaps.add(substrate.getAtomArray()[id.getAtomIndex()].getAtomMap());
      }
    }
    return roAtomMaps;
  }

  /**
   * Build a MolSearch searcher that searches for the given substructure in the given substrate.
   *
   * @param substrate The substrate in which to search.
   * @param substructure The substructure to look for.
   * @return A MolSearch object to perform the given query.
   * @throws SearchException
   */
  private MolSearch getSearcher(Molecule substrate, Molecule substructure) throws SearchException {
    MolSearch searcher = new MolSearch();
    searcher.setSearchOptions(LAX_SEARCH_OPTIONS);
    searcher.setQuery(substructure);
    searcher.setTarget(substrate);
    return searcher;
  }

  /**
   * Extrats the next hit from the searcher and returns the substrate atom maps corresponding to that hit.
   *
   * @param substrate The substrate.
   * @param searcher The searcher.
   * @return The substrate atom maps that match the next search hit.
   * @throws SearchException
   */
  private Set<Integer> getNextSarAtomMap(Molecule substrate, MolSearch searcher) throws SearchException {
    Set<Integer> sarAtomMaps = new HashSet<>();
    SearchHit hit = searcher.findNextHit();

    if (hit == null) {
      return null;
    }

    for (Integer atomId : hit.getSingleHit()) {
      Integer mapValue = substrate.getAtomArray()[atomId].getAtomMap();
      sarAtomMaps.add(mapValue);
    }

    return sarAtomMaps;
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

  public void setSeedReactor(Reactor seedReactor) {
    this.seedReactor = seedReactor;
  }

  public void setSubstrate(Molecule substrate) {
    this.substrate = substrate;
  }

  public void setExpectedProduct(Molecule expectedProduct) {
    this.expectedProduct = expectedProduct;
  }

  public void setSubstructure(Molecule substructure) {
    this.substructure = substructure;
  }
}
