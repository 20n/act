package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class FullReactionBuilderTest {

  private static final String SUBSTRATE_INCHI = "InChI=1S/C7H7NO2/c8-6-4-2-1-3-5(6)7(9)10/h1-4H,8H2,(H,9,10)";
  private static final String PRODUCT_INCHI = "InChI=1S/C8H9NO2/c1-11-8(10)6-4-2-3-5-7(6)9/h2-5H,9H2,1H3";

  private static final String OVERLAP_SUBSTRUCTURE_INCHI = "InChI=1S/C7H6O2/c8-7(9)6-4-2-1-3-5-6/h1-5H,(H,8,9)";
  private static final String DISJOINT_SUBSTRUCTURE_INCHI = "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H";

  private static final String AMBIGUOUS_SUBSTRATE = "InChI=1S/C8H8O3/c9-5-6-1-3-7(4-2-6)8(10)11/h1-4,9H,5H2,(H,10,11)";

  private static final String SEED_REACTION_RULE = "[H][#8:12]-[#6:1]>>[H]C([H])([H])[#8:12]-[#6:1]";

  private static final String FULL_RULE_REACTANT = "InChI=1S/C7H6O2/c8-7(9)6-4-2-1-3-5-6/h1-5H,(H,8,9)";
  private static final String FULL_RULE_PRODUCT = "InChI=1S/C8H8O2/c1-10-8(9)7-5-3-2-4-6-7/h2-6H,1H3";

  private static final String SEED_RULE_REACTANT = "InChI=1S/CH4O/c1-2/h2H,1H3";
  private static final String SEED_RULE_PRODUCT = "InChI=1S/C2H6O/c1-3-2/h1-2H3";

  private static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
    SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }

  private static final String INCHI_SETTINGS = "inchi:AuxNone";

  @Test
  public void FullReactionBuilderFiltersBadRoMatch() throws IOException, ReactionException, SearchException {
    // Arrange
    Molecule substrate = MolImporter.importMol(SUBSTRATE_INCHI);
    Molecule ambiguousSubstrate = MolImporter.importMol(AMBIGUOUS_SUBSTRATE);
    Molecule expectedProduct = MolImporter.importMol(PRODUCT_INCHI);
    Molecule substructure = MolImporter.importMol(OVERLAP_SUBSTRUCTURE_INCHI);

    Reactor seedReactor = new Reactor();
    seedReactor.setReactionString(SEED_REACTION_RULE);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder();

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(substrate, expectedProduct, substructure, seedReactor);

    // Assert
    seedReactor.setReactants(new Molecule[] {ambiguousSubstrate});
    int counter = 0;
    while (seedReactor.react() != null) {
      counter++;
    }
    assertEquals("Seed reactor should produce 2 products.", 2, counter);

    fullReactor.setReactants(new Molecule[] {ambiguousSubstrate});
    counter = 0;
    while (fullReactor.react() != null) {
      counter++;
    }
    assertEquals("Full reactor should produce only 1 product.", 1, counter);

    RxnMolecule rxnMolecule = fullReactor.getReaction();
    String reactant = MolExporter.exportToFormat(rxnMolecule.getComponent(RxnMolecule.REACTANTS, 0), INCHI_SETTINGS);
    String product = MolExporter.exportToFormat(rxnMolecule.getComponent(RxnMolecule.PRODUCTS, 0), INCHI_SETTINGS);
    assertEquals("Reactant of reactor should be as expected.", reactant, FULL_RULE_REACTANT);
    assertEquals("Product of reactor should be as expected.", product, FULL_RULE_PRODUCT);
  }

  @Test
  public void FullReactionBuilderDisjointRegionsKeepsBothMatches() throws IOException, ReactionException, SearchException {
    // Arrange
    Molecule substrate = MolImporter.importMol(SUBSTRATE_INCHI);
    Molecule ambiguousSubstrate = MolImporter.importMol(AMBIGUOUS_SUBSTRATE);
    Molecule expectedProduct = MolImporter.importMol(PRODUCT_INCHI);
    Molecule substructure = MolImporter.importMol(DISJOINT_SUBSTRUCTURE_INCHI);

    Reactor seedReactor = new Reactor();
    seedReactor.setReactionString(SEED_REACTION_RULE);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder();

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(substrate, expectedProduct, substructure, seedReactor);

    // Assert
    seedReactor.setReactants(new Molecule[] {ambiguousSubstrate});
    int counter = 0;
    while (seedReactor.react() != null) {
      counter++;
    }
    assertEquals("Seed reactor should produce 2 products.", 2, counter);

    fullReactor.setReactants(new Molecule[] {ambiguousSubstrate});
    counter = 0;
    while (fullReactor.react() != null) {
      counter++;
    }
    assertEquals("Full reactor should still produce 2 products.", 2, counter);

    RxnMolecule rxnMolecule = fullReactor.getReaction();
    String reactant = MolExporter.exportToFormat(rxnMolecule.getComponent(RxnMolecule.REACTANTS, 0), INCHI_SETTINGS);
    String product = MolExporter.exportToFormat(rxnMolecule.getComponent(RxnMolecule.PRODUCTS, 0), INCHI_SETTINGS);
    assertEquals("Reactant of reactor should be as expected.", reactant, SEED_RULE_REACTANT);
    assertEquals("Product of reactor should be as expected.", product, SEED_RULE_PRODUCT);
  }
}
