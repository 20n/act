package com.act.lcms.v2;


import java.util.Map;
import java.util.Optional;

/**
 * An interface representing a chemical formula.
 * A Chemical Formula is here represented by its elements counts.
 * For example, CH4 is represented as Map(C -> 1, H -> 4) where C and H are the carbon and hydrogen elements.
 * This API provides also mass retrieval, test of match with a structure and a optional name retrieval.
 */

public interface ChemicalFormula<T extends Element> {
  /**
   * Get the formula's element counts
   */
  Map<T, Integer> getElementCounts();

  /**
   * Get the number of a given element in the formula
   */
  Integer getElementCount(T element);

  /**
   * Retrieve the mono-isotopic mass for the formula
   */
  Double getMonoIsotopicMass();

  /**
   * Retrieve an optional formula name
   */
  Optional<String> getName();

  /**
   * Check for equality with another Chemicalformula
   * @param chemicalFormula input formula
   * @return A Boolean, indicating whether or not the formulae match
   */
  Boolean equals(ChemicalFormula chemicalFormula);
}
