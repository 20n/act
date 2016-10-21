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
   * Parses a structure from an InChI string
   * @param inchi input InChI string
   * @return boolean indicating if the parsing operation succeeded.
   */
  Boolean parseStructure(String inchi);

  /**
   * Get the mono-isotopic mass for the molecule
   */
  Double getMass();
}
