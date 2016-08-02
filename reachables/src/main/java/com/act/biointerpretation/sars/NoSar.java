package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

public class NoSar implements Sar, Serializable {
  private static final long serialVersionUID = -7309106064265051106L;

  @JsonProperty("substructure")
  Molecule substructure = null;

  @Override
  public boolean test(List<Molecule> substrates) {
    return true;
  }

  Molecule getSubstructure() {
    return substructure;
  }

  void setSubstructure(Molecule substructure) {
    this.substructure = substructure;
  }
}
