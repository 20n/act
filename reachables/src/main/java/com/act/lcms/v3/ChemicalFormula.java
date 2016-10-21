package com.act.lcms.v3;


import java.util.Map;
import java.util.Optional;

/**
 * An interface representing a chemical formula.
 * A Chemical Formula is here represented by its elements counts.
 * For example, CH4 is represented as Map(C -> 1, H -> 4) where C and H are the carbon and hydrogen elements.
 * This API provides also mass retrieval, test of match with a structure and a optional name retrieval.
 */

public interface ChemicalFormula {
  /**
   * Get the formula's element counts
   */
  Map<Element, Integer> getElementCounts();

  /**
   * Get the number of a given element in the formula
   */
  Integer getElementCount(Element element);

  /**
   * Retrieve the mono-isotopic mass for the formula
   */
  Double getMonoIsotopicMass();

  /**
   * Check whether the formula matches a given molecular structure
   * @param structure input structure
   * @return a boolean, true if the formula matches the structure
   */
  Boolean matchesMolecularStructure(MolecularStructure structure);

  /**
   * Retrieve an optional formula name
   */
  Optional<String> getName();
}
