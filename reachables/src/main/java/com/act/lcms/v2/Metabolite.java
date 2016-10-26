package com.act.lcms.v2;


import java.util.Optional;

/**
 * Representation of a metabolite
 */
public interface Metabolite<M extends MolecularStructure, C extends ChemicalFormula> {

  /**
   * Returns an optional structure
   */
  Optional<M> getStructure();

  /**
   * Returns an optional formula
   */
  Optional<C> getFormula();

  /**
   * Returns the metabolite's monoisotopic mass (in Da)
   */
  Double getMonoIsotopicMass();
}
