package com.act.lcms.v2;


public class RawMetabolite {
  Double monoIsotopicMass;
  String molecule;
  String name;

  public RawMetabolite() {
  }

  public Double getMonoIsotopicMass() {
    return monoIsotopicMass;
  }

  public void setMonoIsotopicMass(Double monoIsotopicMass) {
    this.monoIsotopicMass = monoIsotopicMass;
  }

  public void setMolecule(String molecule) {
    this.molecule = molecule;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMolecule() {
    return molecule;
  }

  public String getName() {
    return name;
  }
}
