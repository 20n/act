package com.act.lcms.v2;

/**
 * Representation of a molecular structure, at first supporting InChIs only
 */
public interface MolecularStructure {
  /**
   * Return the molecule's InChI string representation
   */
  String getInchi();

  /**
   * Get the mono-isotopic mass for the molecule
   */
  Double getMonoIsotopicMass();

  /**
   * Extract the formula for the given structure
   */
  ChemicalFormula getChemicalFormula();
}
