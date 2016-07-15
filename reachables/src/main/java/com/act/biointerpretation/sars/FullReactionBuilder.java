package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FullReactionBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FullReactionBuilder.class);
  private static final String INCHI_SETTINGS = "inchi:AuxNone";

  private static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
    SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }

  int nextLabel;

  public Reactor buildReaction(Molecule substrate, Molecule expectedProduct, Molecule substructure, Reactor seedReactor)
      throws ReactionException, IOException, SearchException {
    nextLabel = 1;
    labelMolecule(substrate);

    seedReactor.setReactants(new Molecule[] {substrate});
    Molecule predictedProduct = runTillProducesProduct(seedReactor, expectedProduct);

    Set<Integer> substrateAtomMaps = getRelevantAtomMaps(substrate, substructure, seedReactor);
    Set<Integer> productAtomMaps = new HashSet(substrateAtomMaps);
    productAtomMaps.addAll(labelAndGetZeroLabeledAtoms(predictedProduct));

    removeIrrelevantPortion(substrate, substrateAtomMaps);
    removeIrrelevantPortion(predictedProduct, productAtomMaps);

    LOGGER.info(MolExporter.exportToFormat(substrate, INCHI_SETTINGS));
    LOGGER.info(MolExporter.exportToFormat(predictedProduct, INCHI_SETTINGS));

    return getFullReactor(substrate, predictedProduct);
  }

  private Reactor getFullReactor(Molecule finalSubstrate, Molecule finalProduct) throws ReactionException {
    RxnMolecule rxnMolecule = new RxnMolecule();
    rxnMolecule.addComponent(finalSubstrate, RxnMolecule.REACTANTS);
    rxnMolecule.addComponent(finalProduct, RxnMolecule.PRODUCTS);

    Reactor fullReactor = new Reactor();
    fullReactor.setReaction(rxnMolecule);
    return fullReactor;
  }

  private void removeIrrelevantPortion(Molecule molecule, Set<Integer> relevantAtoms) {
    for (MolAtom atom : molecule.getAtomArray()) {
      if (!relevantAtoms.contains(atom.getAtomMap())) {
        molecule.removeAtom(atom);
      }
    }
  }

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

  private Set<Integer> getRelevantAtomMaps(Molecule substrate, Molecule substructure, Reactor seedReactor) throws SearchException {
    Set<Integer> roAtomMaps = getRoAtomMaps(substrate, seedReactor);
    Set<Integer> sarAtomMaps = getSarAtomMaps(substrate, substructure);

    Set<Integer> overlap = new HashSet<>(sarAtomMaps);
    overlap.retainAll(roAtomMaps);

    // If the overlap is empty we don't want to build an extended RO- this indicates RO and SAR affect different
    // parts of the molecule.
    if (!overlap.isEmpty()) {
      roAtomMaps.addAll(sarAtomMaps);
    }
    return roAtomMaps;
  }

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

  private Set<Integer> getSarAtomMaps(Molecule substrate, Molecule substructure) throws SearchException {
    Set<Integer> sarAtomMaps = new HashSet<>();

    MolSearch searcher = new MolSearch();
    searcher.setSearchOptions(SEARCH_OPTIONS);
    searcher.setQuery(substructure);
    searcher.setTarget(substrate);
    SearchHit hit = searcher.findFirstHit();

    for (Integer atomId : hit.getSingleHit()) {
      Integer mapValue = substrate.getAtomArray()[atomId].getAtomMap();
      sarAtomMaps.add(mapValue);
    }

    return sarAtomMaps;
  }

  private void labelMolecule(Molecule mol) {
    for (MolAtom atom : mol.getAtomArray()) {
      if (atom.getAtomMap() == 0) {
        atom.setAtomMap(nextLabel);
        nextLabel++;
      }
    }
  }

  private Molecule runTillProducesProduct(Reactor reactor, Molecule expectedProduct)
      throws ReactionException, IOException {
    Molecule[] products;
    Molecule product = null;
    String expectedInchi = MolExporter.exportToFormat(expectedProduct, INCHI_SETTINGS);
    while ((products = reactor.react()) != null) {
      if (MolExporter.exportToFormat(products[0], INCHI_SETTINGS).equals(expectedInchi)) {
        product = products[0];
        break;
      }
    }
    if (product == null) {
      throw new NullPointerException("Reactor doesn't produce expected product on given substrate.");
    }
    return product;
  }
}
