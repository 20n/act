package com.act.lcms.v2;

/**
 * Representation of an element isotope, such as C-13
 */
public interface ElementIsotope extends Atom {
  /**
   * Get the abundance of the isotope (probability in [0, 1])
   */
  Double getAbundance();
}
