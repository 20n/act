/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.sars;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
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

  static final ReactionProjector PROJECTOR = new ReactionProjector();
  static final McsCalculator mockMcs = Mockito.mock(McsCalculator.class);
  static final Reactor DUMMY_SEED_REACTOR = new Reactor();

  ExpandedReactionSearcher mockSearcher = Mockito.mock(ExpandedReactionSearcher.class);

  private Molecule substrate1, substrate2, product1, product2;
  private RxnMolecule rxn1, rxn2;
  List<RxnMolecule> rxnMoleculeList;

  private Reactor reactorMatch = new Reactor();
  private Reactor reactorMismatch = new Reactor();


  @Before
  public void init() throws MolFormatException, ReactionException {
    substrate1 = MolImporter.importMol(SUBSTRATE_1, INCHI_IMPORT_SETTINGS);
    product1 = MolImporter.importMol(PRODUCT_1, INCHI_IMPORT_SETTINGS);
    rxn1 = new RxnMolecule();
    rxn1.addComponent(substrate1, RxnMolecule.REACTANTS);
    rxn1.addComponent(product1, RxnMolecule.PRODUCTS);

    substrate2 = MolImporter.importMol(SUBSTRATE_2, INCHI_IMPORT_SETTINGS);
    product2 = MolImporter.importMol(PRODUCT_2, INCHI_IMPORT_SETTINGS);
    rxn2 = new RxnMolecule();
    rxn2.addComponent(substrate2, RxnMolecule.REACTANTS);
    rxn2.addComponent(product2, RxnMolecule.PRODUCTS);

    rxnMoleculeList = Arrays.asList(rxn1, rxn2);

    reactorMatch.setReactionString(REACTOR_STRING_MATCH);
    reactorMismatch.setReactionString(REACTOR_STRING_MISMATCH);
  }

  @Test
  public void testTwoReactionsOneReactorMatchesBoth() throws ReactionException, IOException {
    // Arrange
    Mockito.when(mockSearcher.getNextReactor())
        .thenReturn(reactorMatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(rxnMoleculeList, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should be as returned by the searcher.", reactorMatch, fullReactor);
  }


  @Test
  public void testTwoReactionsOneReactorMatchesOnlyOne() throws ReactionException {
    // Arrange
    Mockito.when(mockSearcher.getNextReactor())
        .thenReturn(reactorMismatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(rxnMoleculeList, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should return seed reactor only.", DUMMY_SEED_REACTOR, fullReactor);
  }

  @Test
  public void testTwoReactionsTwoReactorsSecondMatches() throws ReactionException {
    // Arrange
    Mockito.when(mockSearcher.getNextReactor())
        .thenReturn(reactorMismatch)
        .thenReturn(reactorMatch)
        .thenReturn(null);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(mockMcs, mockSearcher, PROJECTOR);

    // Act
    Reactor fullReactor = reactionBuilder.buildReaction(rxnMoleculeList, DUMMY_SEED_REACTOR);

    // Assert
    assertEquals("Reaction should be the one that matches the reactions.", reactorMatch, fullReactor);

  }
}
