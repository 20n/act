package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FullReactionBuilderTest {

  private static final String INCHI_IMPORT_SETTINGS = "inchi";

  static final String SUBSTRATE_1 = "InChI=1S/C4H6O3/c1-2-3(5)4(6)7/h2H2,1H3,(H,6,7)";
  static final String PRODUCT_1 = "InChI=1S/C4H9NO2/c1-2-3(5)4(6)7/h3H,2,5H2,1H3,(H,6,7)";

  static final String SUBSTRATE_2 = "InChI=1S/C4H6O2/c1-2-4(6)3-5/h3H,2H2,1H3";
  static final String PRODUCT_2 = "InChI=1S/C4H9NO/c1-2-4(5)3-6/h3-4H,2,5H2,1H3";

  static final String REACTOR_STRING_MATCH =
      "[#6:7]-[#6:6]-[#6:8](=[O:10])-[#6:9]=[#8:11]>>[#6:7]-[#6:6]-[#6:8](-[#7:21])-[#6:9]=[#8:11]";
  static final String REACTOR_STRING_MISMATCH =
      "[#6:7]-[#6:6]-[#6:8](=[O:10])-[#6:9](-[#8:11])=[O:12]>>[#6:7]-[#6:6]-[#6:8](-[#7:21])-[#6:9](-[#8:11])=[O:12]";

  static final List<Reaction> DUMMY_REACTION_LIST = new ArrayList<>();

  static final ReactionProjector PROJECTOR = new ReactionProjector();
  static final McsCalculator mockMcs = Mockito.mock(McsCalculator.class);
  static final Reactor DUMMY_SEED_REACTOR = new Reactor();

  DbAPI mockDb = Mockito.mock(DbAPI.class);
  GeneralReactionSearcher mockSearcher = Mockito.mock(GeneralReactionSearcher.class);

  private Molecule substrate1, substrate2, product1, product2;
  private Reactor reactorMatch = new Reactor();
  private Reactor reactorMismatch = new Reactor();


  @Before
  public void init() throws MolFormatException, ReactionException {
    substrate1 = MolImporter.importMol(SUBSTRATE_1, INCHI_IMPORT_SETTINGS);
    substrate2 = MolImporter.importMol(SUBSTRATE_2, INCHI_IMPORT_SETTINGS);
    product1 = MolImporter.importMol(PRODUCT_1, INCHI_IMPORT_SETTINGS);
    product2 = MolImporter.importMol(PRODUCT_2, INCHI_IMPORT_SETTINGS);

    reactorMatch.setReactionString(REACTOR_STRING_MATCH);
    reactorMismatch.setReactionString(REACTOR_STRING_MISMATCH);

    Mockito.when(mockDb.getFirstSubstratesAsMolecules(DUMMY_REACTION_LIST))
        .thenReturn(Arrays.asList(substrate1, substrate2));
    Mockito.when(mockDb.getFirstProductsAsMolecules(DUMMY_REACTION_LIST))
        .thenReturn(Arrays.asList(product1, product2));
  }

  @Test
  public void testTwoReactionsOneReactorMatchesBoth() throws ReactionException, IOException {
    // Arrange
    Mockito.when(mockSearcher.getNextGeneralization())
        .thenReturn(reactorMatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockDb, mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(DUMMY_REACTION_LIST, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should be as returned by the searcher.", reactorMatch, fullReactor);
  }


  @Test
  public void testTwoReactionsOneReactorMatchesOnlyOne() throws ReactionException {
    // Arrange
    Mockito.when(mockSearcher.getNextGeneralization())
        .thenReturn(reactorMismatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockDb, mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(DUMMY_REACTION_LIST, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should return seed reactor only.", DUMMY_SEED_REACTOR, fullReactor);
  }

  @Test
  public void testTwoReactionsTwoReactorsSecondMatches() throws ReactionException {
    // Arrange
    Mockito.when(mockSearcher.getNextGeneralization())
        .thenReturn(reactorMismatch)
        .thenReturn(reactorMatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockDb, mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(DUMMY_REACTION_LIST, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should be the one that matches the reactions.", reactorMatch, fullReactor);

  }
}
