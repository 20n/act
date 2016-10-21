package com.act.lcms.v3;


import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Representation of a metabolite
 */
public interface Metabolite {
  /**
   * Returns an optional structure
   */
  Optional<MolecularStructure> getStructure();
  /**
   * Returns an optional formula
   */
  Optional<ChemicalFormula> getFormula();
  /**
   * Returns the metabolite's monoisotopic mass
   */
  Double getMonoIsotopicMass();
  /**
   * Returns a list of isotopes
   */
  List<Isotope> getIsotopes();
  /**
   * Returns a mapping from isotope mass to probability, the isotope mass distribution
   */
  Map<Double, Double> getIsotopesMassDistribution();
}
