package com.act.lcms.v3;

/**
 * Representation of an element isotope, such as C-13
 */
public interface ElementIsotope {
  /**
   * Get the base element for this isotope.
   * In the example of C-13, the base element is Carbon
   */
  Element getElement();

  /**
   * Get the mass number (# protons + # neutrons) of this isotope.
   * In the example of C-13, the mass number is 13
   */
  Integer getMassNumber();

  /**
   * Get the abundance of the isotope (probability in [0, 1])
   */
  Double getAbundance();
}
