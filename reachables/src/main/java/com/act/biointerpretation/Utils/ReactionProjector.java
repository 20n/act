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
    // If there is only one reactant, we can do just a simple reaction computation. However, if we have multiple reacants,
    // we have to us the ConcurrentReactorProcessor API since it gives us the ability to combinatorially explore all
    // possible matching combinations of reactants on the substrates of the RO.
    if (mols.length == 1) {
      reactor.setReactants(mols);
      Molecule[] products = reactor.react();
      return products;
    } else {
      ConcurrentReactorProcessor reactorProcessor = new ConcurrentReactorProcessor();
      reactorProcessor.setReactor(reactor);

      // This step is needed by ConcurrentReactorProcessor for the combinatorial exploration.
      // The iterator takes in the same substrates in each iteration step to build a matrix of combinations.
      // For example, if we have A + B -> C, the iterator creates this array: [[A,B], [A,B]]. Based on this,
      // ConcurrentReactorProcessor will cross the elements in the first index with the second, creating the combinations
      // A+A, A+B, B+A, B+B and operates all of those on the RO, which is what is desired.
      MoleculeIterator[] iterator = new MoleculeIterator[mols.length];
      for (int i = 0; i < mols.length; i++) {
        iterator[i] = MoleculeIteratorFactory.createMoleculeIterator(mols);
      }

      reactorProcessor.setReactantIterators(iterator, ConcurrentReactorProcessor.MODE_COMBINATORIAL);
      return reactorProcessor.react();
    }
  }
}

