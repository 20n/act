package com.act.lcms.v2;


public class RawMetabolite {
  private Float monoIsotopicMass;
  private String molecule;
  private String name;

  public RawMetabolite() {
  }

  public Float getMonoIsotopicMass() {
    return monoIsotopicMass;
  }

  public void setMonoIsotopicMass(Float monoIsotopicMass) {
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
