package com.act.lcms.v2;

import java.util.List;

/**
 * An interface representing a chemical element such as C, H or O
 */
public interface Element {

  /**
   * Get the symbol of the element (for example, "C" for carbon)
   */
  String getSymbol();

  /**
   * Get the atomic number (# protons) of the element (for example, 12 for carbon)
   */
  Integer getAtomicNumber();

  /**
   * Get the valency of the element (for example, 4 for carbon)
   * The valency represents the combining power of the element
   */
  Integer getValency();

  /**
   * Get the mass of the most frequent natural isotope
   */
  Double getMass();

  /**
   * Get the isotopes of the element
   */
  List<ElementIsotope> getElementIsotopes();
}
