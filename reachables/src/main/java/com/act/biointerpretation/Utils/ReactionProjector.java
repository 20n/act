package com.act.biointerpretation.Utils;

import chemaxon.reaction.ConcurrentReactorProcessor;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.util.iterator.MoleculeIterator;
import chemaxon.util.iterator.MoleculeIteratorFactory;

import java.io.IOException;

public class ReactionProjector {

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the product of the transformation.
   *
   * @param mols An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The product of the reaction
   */
  public static Molecule[] projectRoOnMolecules(Molecule[] mols, Reactor reactor) throws ReactionException, IOException {
    if (mols.length == 1) {
      reactor.setReactants(mols);
      Molecule[] products = reactor.react();
      return products;
    } else {
      ConcurrentReactorProcessor crp = new ConcurrentReactorProcessor();
      crp.setReactor(reactor);

      MoleculeIterator[] iterator = new MoleculeIterator[mols.length];
      for (int i = 0; i < mols.length; i++) {
        iterator[i] = MoleculeIteratorFactory.createMoleculeIterator(mols);
      }

      crp.setReactantIterators(iterator, ConcurrentReactorProcessor.MODE_COMBINATORIAL);
      return crp.react();
    }
  }
}

