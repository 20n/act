package com.act.biointerpretation.sars;

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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RoBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(RoBuilder.class);

  private static final String SUBSTRATE_INCHI = "InChI=1S/C7H7NO2/c8-6-4-2-1-3-5(6)7(9)10/h1-4H,8H2,(H,9,10)";
  private static final String PRODUCT_INCHI = "InChI=1S/C8H9NO2/c1-11-8(10)6-4-2-3-5-7(6)9/h2-5H,9H2,1H3";

  private static final String SUBSTRUCTURE_INCHI = "InChI=1S/C7H6O2/c8-7(9)6-4-2-1-3-5-6/h1-5H,(H,8,9)";

  private static final String SEED_REACTION_RULE = "[H][#8:12]-[#6:1]>>[H]C([H])([H])[#8:12]-[#6:1]";
  private static final String FULL_REACTION_RULE = "[H][#8:10]-[#6:1](=[O:2])-[c:3]1[c:4][c:5][c:6][c:7][c:8]1>>[H]C([H])([H])[#8:10]-[#6:1](=[O:2])-[c:3]1[c:4][c:5][c:6][c:7][c:8]1";

  private static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
    SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }


  public static void main(String[] args) throws MolFormatException, ReactionException, SearchException {
    // Made sure there's only one product produced
    // Tested that atom labels DO get propagated through a reaction
    // Theory: atoms with positive Identifier/atom, and non-zero schema in the reactionMap are exactly those
    // which are explicitly included in the RO

    Molecule substrate = MolImporter.importMol(SUBSTRATE_INCHI);
    Molecule product = MolImporter.importMol(PRODUCT_INCHI);
    Molecule substructure = MolImporter.importMol(SUBSTRUCTURE_INCHI);

    Reactor reactor = new Reactor();
    reactor.setReactionString(SEED_REACTION_RULE);
    reactor.setReactants(new Molecule[] {substrate});
    Molecule predictedProduct = reactor.react()[0];

//    printAll(substrate, predictedProduct, substructure);
//
    int labeler = 1;
    for (MolAtom atom : substrate.getAtomArray()) {
      atom.setAtomMap(labeler);
      labeler++;
    }

    reactor.setReactants(new Molecule[] {substrate});
    predictedProduct = reactor.react()[0];

    Map<MolAtom, AtomIdentifier> reactionMap = reactor.getReactionMap();

    Set<MolAtom> roSubstrateAtoms = new HashSet<>();
    for (MolAtom atom : reactionMap.keySet()) {
      AtomIdentifier id = reactionMap.get(atom);
      if (id.getAtomIndex() > 0 && id.getReactionSchemaMap() > 0) {
        roSubstrateAtoms.add(substrate.getAtomArray()[id.getAtomIndex()]); // add substrate atom if it's in RO
      }
    }

    Set<Integer> sarSubstrateAtomIndices = new HashSet<>();

    MolSearch searcher = new MolSearch();
    searcher.setSearchOptions(SEARCH_OPTIONS);
    searcher.setQuery(substructure);
    searcher.setTarget(substrate);
    SearchHit hit = searcher.findFirstHit();

    for (Integer atomId : hit.getSingleHit()) {
      sarSubstrateAtomIndices.add(atomId);
    }

    // Make set for product ones
    for (MolAtom atom : reactionMap.keySet()) {
      AtomIdentifier id = reactionMap.get(atom);
      if (sarSubstrateAtomIndices.contains(id.getAtomIndex())) {
        // Add to set
      }
    }

    printAll(substrate, predictedProduct, substructure);
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
