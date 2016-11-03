package com.act.lcms.v2;


import java.util.Optional;

public class LcmsMetabolite implements Metabolite {

  private MolecularStructure structure;
  private ChemicalFormula formula;
  private Double monoisotopicMass;

  LcmsMetabolite(Double monoisotopicMass) {
    this.monoisotopicMass = monoisotopicMass;
  }

  LcmsMetabolite(Double monoisotopicMass, MolecularStructure structure) {
    this.monoisotopicMass = monoisotopicMass;
    this.structure = structure;
    this.formula = null;
  }

  LcmsMetabolite(Double monoisotopicMass, ChemicalFormula formula) {
    this.monoisotopicMass = monoisotopicMass;
    this.formula = formula;
    this.structure = null;
  }

  public Optional<MolecularStructure> getStructure() {
    return Optional.ofNullable(this.structure);
  }

  public Optional<ChemicalFormula> getFormula() {
    return Optional.ofNullable(this.formula);
  }

  public Double getMonoIsotopicMass() {
    return this.monoisotopicMass;
  }

}
