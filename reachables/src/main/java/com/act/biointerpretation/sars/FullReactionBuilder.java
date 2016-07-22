package com.act.biointerpretation.sars;

import chemaxon.calculations.hydrogenize.Hydrogenize;
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
import chemaxon.struc.MoleculeGraph;
import chemaxon.struc.RxnMolecule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FullReactionBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FullReactionBuilder.class);
  private static final String INCHI_SETTINGS = "inchi:AuxNone";

  private static final MolSearchOptions LAX_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  private static final Hydrogenize HYDROGENIZER = new Hydrogenize();

  static {
    LAX_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE);
    LAX_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }

  int nextLabel;

  public Reactor buildReaction(Molecule substrate, Molecule expectedProduct, Molecule substructure, Reactor seedReactor) throws IOException, ReactionException, SearchException {
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

    Molecule predictedProduct = null;
    try {
      predictedProduct = runTillProducesProduct(seedReactor, expectedProduct);
    } catch (ReactionException e) {
      LOGGER.warn("ReactionException on runTillProducesProduct. %s", e.getMessage());
      throw e;
    } catch (IOException e) {
      LOGGER.warn("IOException on runTillProducesProduct. %s", e.getMessage());
      throw e;
    }

    Set<Integer> substrateAtomMaps = null;
    try {
      substrateAtomMaps = getRelevantAtomMaps(substrate, substructure, seedReactor);
    } catch (SearchException e) {
      LOGGER.warn("SearchException on getRelevantAtoMMaps. %s", e.getMessage());
      throw e;
    }
    Set<Integer> productAtomMaps = new HashSet(substrateAtomMaps);
    productAtomMaps.addAll(labelAndGetZeroLabeledAtoms(predictedProduct));

    removeIrrelevantPortion(substrate, substrateAtomMaps);
    removeIrrelevantPortion(predictedProduct, productAtomMaps);

    try {
      LOGGER.warn(MolExporter.exportToFormat(substrate, INCHI_SETTINGS));
      LOGGER.warn(MolExporter.exportToFormat(predictedProduct, INCHI_SETTINGS));
    } catch (IOException e) {
      LOGGER.warn("Failed to export substrate and predicted product to inchi. %s", e.getMessage());
      throw e;
    }

    try {
      return getFullReactor(substrate, predictedProduct);
    } catch (ReactionException e) {
      LOGGER.warn("Failed to getFullReactor. %s", e.getMessage());
      throw e;
    }
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

    try {
      LOGGER.info("Substrate: %s", MolExporter.exportToFormat(substrate, INCHI_SETTINGS));
      LOGGER.info("Substructure: %s", MolExporter.exportToFormat(substructure, INCHI_SETTINGS));
    } catch (IOException e) {
      LOGGER.warn("PRINT ERROR.");
    }
    MolSearch searcher = new MolSearch();
    searcher.setSearchOptions(LAX_SEARCH_OPTIONS);
    searcher.setQuery(substructure);
    searcher.setTarget(substrate);
    SearchHit hit = searcher.findFirstHit();
    if (hit == null) {
      LOGGER.error("No hit!");
    }

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
    Sar leftSar = new OneSubstrateSubstructureSar(expectedProduct, LAX_SEARCH_OPTIONS);
    LOGGER.info("Substrate: %s", MolExporter.exportToFormat(reactor.getReactants()[0], INCHI_SETTINGS));
    LOGGER.info("Reactor: %s", MolExporter.exportToFormat(reactor.getReaction(), "smarts"));
    LOGGER.info("Expected product: %s", MolExporter.exportToFormat(expectedProduct, INCHI_SETTINGS));
    while ((products = reactor.react()) != null) {
      HYDROGENIZER.convertExplicitHToImplicit(products[0]);
      LOGGER.info("Produced product: %s", MolExporter.exportToFormat(products[0], INCHI_SETTINGS));
      if (leftSar.test(Arrays.asList(products[0]))) {
        LOGGER.info("First substructure match.");
        Sar rightSar = new OneSubstrateSubstructureSar(products[0], LAX_SEARCH_OPTIONS);
        if (rightSar.test(Arrays.asList(expectedProduct))) {
          LOGGER.info("Second substructure match.");
          // Since we know this reactor
          return products[0];
        }
      }
    }
    LOGGER.error("Reactor doesn't produce expected product.");
    throw new IllegalArgumentException("Expected product not among Reactor's predictions.");
  }
}
