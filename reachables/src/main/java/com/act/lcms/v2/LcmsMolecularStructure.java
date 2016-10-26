package com.act.lcms.v2;


import org.apache.commons.lang.NotImplementedException;

public class LcmsMolecularStructure implements MolecularStructure {

  private String inchi;
  private Double monoisotopicMass;

  public String getInchi() {
    return this.inchi;
  }

  public Double getMonoIsotopicMass() {
    return this.monoisotopicMass;
  }

  public LcmsChemicalFormula getChemicalFormula() {
    throw new NotImplementedException();
  }
}
