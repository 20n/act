package com.act.lcms.v3;


import java.util.Map;
import java.util.Optional;

/**
 * An interface representing a chemical formula
 */
public interface ChemicalFormula {
  /**
   * Get the count of elements in the formula
   * @return a mapping between elements and their count in the formula
   */
  Map<Element, Integer> getElementCounts();

  /**
   * Returns a count for a specific element
   * @param element: element to return the count for
   * @return the count
   */
  Integer getElementCount(Element element);

  /**
   * Retrive the monoisotopic mass of the molecule
   * @return the molecule's mass
   */
  Double getMonoIsotopicMass();

  /**
   * Check whether the formula matches a given molecular structure
   * @param structure input structure
   * @return a boolean, true if the formula matches the given structure
   */
  Boolean matchesMolecularStructure(MolecularStructure structure);

  /**
   * Retrives an Optional with the formula's name
   * @return the name if it exists
   */
  Optional<String> getName();
}
