package com.act.lcms.v2;

/**
 * Representation of an isotope.
 * An isotope is defined here by its base metabolite, its isotopic mass and abundance.
 */
public interface Isotope {
  /**
   * Get the metabolite from which this isotope is derived
   */
  Metabolite getMetabolite();

  /**
   * Get the isotopic mass of the isotope (in Da)
   */
  Double getIsotopicMass();

  /**
   * Get the abundance of the isotope (probability [0, 1])
   */
  Double getAbundance();
}
