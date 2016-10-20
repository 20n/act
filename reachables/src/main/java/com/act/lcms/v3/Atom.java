package com.act.lcms.v3;

/**
 * An interface representing an atom, defined simply by a number of neutrons, protons, electrons
 */
public interface Atom {
  /**
   * Get the mass number (# protons + # neutrons)
   * @return the mass number
   */
  Integer getMassNumber();
  /**
   * Get the atomic number (# protons)
   * @return the atomic number
   */
  Integer getAtomicNumber();
}
