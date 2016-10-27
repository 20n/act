package com.act.lcms.v3;

/**
 * Created by tom on 10/12/16.
 */
public class NamedMolecule {
  Float mass;
  String molecule;
  String name;

  public NamedMolecule() {
  }

  public Float getMass() {
    return mass;
  }

  public void setMass(Float mass) {
    this.mass = mass;
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
