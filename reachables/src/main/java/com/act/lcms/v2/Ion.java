package com.act.lcms.v2;


import com.act.lcms.MS1;

/**
 * Representation of an Ion for the purpose of LCMS analysis
 */
public interface Ion {
  /**
   * Return the underlying isotope
   */
  Isotope getIsotope();

  /**
   * Return the ion m/z value
   */
  Double getMzValue();

  /**
   * Return the ion type
   */
  MS1.MetlinIonMass getIonType();
}
