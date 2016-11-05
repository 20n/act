package com.act.lcms.v2;


import java.util.Optional;

/**
 * A lightweight class to hold metabolites parsed from an enumerated file.
 * Keeping metabolites as strings allows to parse a corpus of size ~10M in a matter of seconds in a NavigableMap.
 * Conversions to Metabolite after filtering by mass, on a subset of interest.
 * TODO: evaluate performance of a large corpus with direct conversion to Metabolites
 */

public class RawMetabolite {

  private final Double monoIsotopicMass;
  private final String molecule;
  private final String name;

  public RawMetabolite(Double monoIsotopicMass, String molecule, String name) {
    this.monoIsotopicMass = monoIsotopicMass;
    this.molecule = molecule;
    this.name = name;
  }

  public Double getMonoIsotopicMass() {
    return monoIsotopicMass;
  }

  public String getMolecule() {
    return molecule;
  }

  public Optional<String> getName() {
    return Optional.ofNullable(this.name);
  }
}
