package com.act.lcms.v3;

import java.util.List;

/**
 * An interface representing a chemical element such as C, H or O
 * Could be implemented with the help of Chemaxon's PeriodicSystem
 * https://www.chemaxon.com/jchem/doc/dev/java/api/chemaxon/struc/PeriodicSystem.html
 */
public interface Element {
  /**
   * Getter for the symbol of the element
   * @return the symbol of the element
   */
  String getSymbol();

  /**
   * Get the atomic number (# protons) of the element
   * @return the atomic number of the element
   */
  Integer getAtomicNumber();

  /**
   * Get the valency of the element (combining power of the element)
   * @return the valency of the element
   */
  Integer getValency();

  /**
   * Get the isotopes of the element
   * @return a list of isotopes
   */
  List<ElementIsotope> getElementIsotopes();
}
