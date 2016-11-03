package com.act.lcms.v2;


import java.util.Optional;

/**
 * Representation of a metabolite
 */
public interface Metabolite {

  /**
   * Returns an optional structure
   */
  Optional<MolecularStructure> getStructure();

  /**
   * Returns an optional formula
   */
  Optional<ChemicalFormula> getFormula();

  /**
   * Returns the metabolite's mono-isotopic mass (in Da)
   */
  Double getMonoIsotopicMass();
}
