package com.act.biointerpretation.Utils;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;

public class ReactionProjector {

  /**
   * This function takes as input an array of molecules and a Reactor and outputs the product of the transformation.
   *
   * @param mols An array of molecules representing the chemical reactants.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The product of the reaction
   */
  public static Molecule[] projectRoOnMolecules(Molecule[] mols, Reactor reactor) throws ReactionException {
    reactor.setReactants(mols);
    Molecule[] products = reactor.react();
    return products;
  }
}

