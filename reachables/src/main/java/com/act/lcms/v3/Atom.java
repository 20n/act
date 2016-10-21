package com.act.lcms.v3;

/**
 * An interface representing an atom.
 * The atom is defined by its mass and atomic numbers.
 */
public interface Atom {
  /**
   * Get the mass number (# protons + # neutrons)
   */
  Integer getMassNumber();
  /**
   * Get the atomic number (# protons)
   */
  Integer getAtomicNumber();

  /**
   * Get the base element for this atom.
   * In the example of C-13, the base element is Carbon
   */
  Element getElement();
}
