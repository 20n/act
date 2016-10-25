package com.act.lcms.v2;

/**
 * Representation of an element isotope, such as C-13
 */
public interface ElementIsotope {

  /**
   * Get the mass number (# protons + # neutrons)
   */
  Integer getMassNumber();

  /**
   * Get the base element for this atom.
   * In the example of C-13, the base element is Carbon
   */
  Element getElement();

  /**
   * Get the abundance of the isotope (probability in [0, 1])
   */
  Double getAbundance();
}
