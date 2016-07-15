package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.sketch.modules.AtomMapper;
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
import com.act.biointerpretation.l2expansion.PredictionCorpusRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RoBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(RoBuilder.class);

  private static final String SUBSTRATE_INCHI = "InChI=1S/C7H7NO2/c8-6-4-2-1-3-5(6)7(9)10/h1-4H,8H2,(H,9,10)";
  private static final String PRODUCT_INCHI = "InChI=1S/C8H9NO2/c1-11-8(10)6-4-2-3-5-7(6)9/h2-5H,9H2,1H3";

  private static final String SUBSTRUCTURE_INCHI = "InChI=1S/C7H6O2/c8-7(9)6-4-2-1-3-5-6/h1-5H,(H,8,9)";

  private static final String AMBIGUOUS_SUBSTRATE = "InChI=1S/C8H8O3/c9-5-6-1-3-7(4-2-6)8(10)11/h1-4,9H,5H2,(H,10,11)";

  private static final String SEED_REACTION_RULE = "[H][#8:12]-[#6:1]>>[H]C([H])([H])[#8:12]-[#6:1]";
  private static final String FULL_REACTION_RULE = "[H][#8:10]-[#6:1](=[O:2])-[c:3]1[c:4][c:5][c:6][c:7][c:8]1>>[H]C([H])([H])[#8:10]-[#6:1](=[O:2])-[c:3]1[c:4][c:5][c:6][c:7][c:8]1";
  private static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
    SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }

  private static final String INCHI_SETTINGS = "inchi:AuxNone";

  public static void main(String[] args) throws IOException, ReactionException, SearchException {
    // Made sure there's only one product produced
    // Tested that atom labels DO get propagated through a reaction
    // Theory: atoms with positive Identifier/atom, and non-zero schema in the reactionMap are exactly those
    // which are explicitly included in the RO

    Molecule substrate = MolImporter.importMol(SUBSTRATE_INCHI);
    Molecule ambiguousSubstrate = MolImporter.importMol(AMBIGUOUS_SUBSTRATE);
    Molecule expectedProduct = MolImporter.importMol(PRODUCT_INCHI);
    Molecule substructure = MolImporter.importMol(SUBSTRUCTURE_INCHI);

    Reactor seedReactor = new Reactor();
    seedReactor.setReactionString(SEED_REACTION_RULE);
    seedReactor.setReactants(new Molecule[]{substrate});

    int labeler = 1;
    for (MolAtom atom : substrate.getAtomArray()) {
      atom.setAtomMap(labeler);
      labeler++;
    }

    seedReactor.setReactants(new Molecule[]{substrate});

    Molecule[] predictedProducts;
    Molecule predictedProduct = null;
    while ((predictedProducts = seedReactor.react()) != null) {
      predictedProduct = predictedProducts[0];
      if (MolExporter.exportToFormat(predictedProduct, INCHI_SETTINGS).equals(PRODUCT_INCHI)) {
        break;
      }
    }
    if (predictedProduct == null) {
      LOGGER.error("Didn't find expected product.");
      System.exit(0);
    }

    Map<MolAtom, AtomIdentifier> reactionMap = seedReactor.getReactionMap();

    Set<Integer> roSubstrateMapVal =  new HashSet<>();
    for (MolAtom atom : reactionMap.keySet()) {
      AtomIdentifier id = reactionMap.get(atom);
      if (id.getAtomIndex() > 0 && id.getReactionSchemaMap() > 0) {
        roSubstrateMapVal.add(substrate.getAtomArray()[id.getAtomIndex()].getAtomMap()); // add substrate atom if it's in RO
      }
      LOGGER.info("Keyed atom's map value, atom index: %d, %d", atom.getAtomMap(), id.getAtomIndex());
    }

    Set<Integer> sarSubstrateMapVal = new HashSet<>();

    MolSearch searcher = new MolSearch();
    searcher.setSearchOptions(SEARCH_OPTIONS);
    searcher.setQuery(substructure);
    searcher.setTarget(substrate);
    SearchHit hit = searcher.findFirstHit();

    for (Integer atomId : hit.getSingleHit()){
      Integer mapValue = substrate.getAtomArray()[atomId].getAtomMap();
      sarSubstrateMapVal.add(mapValue);
    }

    Set<Integer> substrateMapVal = new HashSet<>(sarSubstrateMapVal);
    substrateMapVal.addAll(roSubstrateMapVal);
    Set<Integer> productMapVal = new HashSet<>(substrateMapVal);

    for (MolAtom atom : predictedProduct.getAtomArray()) {
      if (atom.getAtomMap() == 0) {
        atom.setAtomMap(labeler);
        productMapVal.add(labeler);
        labeler++;
      }
    }

    LOGGER.info("ro map vals:");
    for (Integer i : roSubstrateMapVal) {
      LOGGER.info(i);
    }
    LOGGER.info("sar map vals:");
    for (Integer i : sarSubstrateMapVal) {
      LOGGER.info(i);
    }
    LOGGER.info("product map vals:");
    for (Integer i : productMapVal) {
      LOGGER.info(i);
    }

    LOGGER.info("Final mapped molecules, everything:");
    printAll(substrate, predictedProduct, substructure);

    for (MolAtom atom : substrate.getAtomArray()) {
      if (!substrateMapVal.contains(atom.getAtomMap())) {
        substrate.removeAtom(atom);
      }
    }


    for (MolAtom atom : predictedProduct.getAtomArray()) {
      if (!productMapVal.contains(atom.getAtomMap())) {
        predictedProduct.removeAtom(atom);
      }
    }

    LOGGER.info("Final molecules, full RO only:");
    printAll(substrate, predictedProduct, substructure);
    LOGGER.info("Substrate: %s", MolExporter.exportToFormat(substrate, INCHI_SETTINGS));
    LOGGER.info("Predicted product: %s", MolExporter.exportToFormat(predictedProduct, INCHI_SETTINGS));

    RxnMolecule rxnMolecule = new RxnMolecule();
    rxnMolecule.addComponent(substrate, RxnMolecule.REACTANTS);
    rxnMolecule.addComponent(predictedProduct, RxnMolecule.PRODUCTS);

    Reactor fullReactor = new Reactor();
    fullReactor.setReaction(rxnMolecule);

    LOGGER.info("RxnMolecule : %s", MolExporter.exportToFormat(rxnMolecule, INCHI_SETTINGS));

    seedReactor.setReactants(new Molecule[]{ambiguousSubstrate});
    Molecule[] products;
    LOGGER.info("Seed reactor produces:");
    while ((products = seedReactor.react()) != null) {
      Molecule thisProduct = products[0];
      LOGGER.info(MolExporter.exportToFormat(thisProduct, INCHI_SETTINGS));
    }

    fullReactor.setReactants(new Molecule[]{ambiguousSubstrate});
    LOGGER.info("Full reactor produces:");
    while ((products = fullReactor.react()) != null) {
      Molecule thisProduct = products[0];
      LOGGER.info(MolExporter.exportToFormat(thisProduct, INCHI_SETTINGS));
    }



  }

  private static void printMolecule(Molecule mol) {
    for (MolAtom atom : mol.getAtomArray()) {
      LOGGER.info("Number, map: %d. %s", atom.getAtno(), atom.getAtomMap());
    }
  }

  private static void printAll(Molecule substrate, Molecule product, Molecule substructure) {
    LOGGER.info("Printing substrate:");
    printMolecule(substrate);
    LOGGER.info("Printing product:");
    printMolecule(product);
    LOGGER.info("Printing substructure:");
    printMolecule(substructure);
  }


}
