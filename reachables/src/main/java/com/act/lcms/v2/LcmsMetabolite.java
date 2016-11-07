package com.act.lcms.v2;


import java.util.Optional;

public class LcmsMetabolite implements Metabolite {

  private MolecularStructure structure;
  private ChemicalFormula formula;
  private Double monoisotopicMass;

  LcmsMetabolite(Double monoisotopicMass) {
    this.monoisotopicMass = monoisotopicMass;
    this.formula = null;
    this.structure = null;
  }

  LcmsMetabolite(ChemicalFormula formula) {
    this.formula = formula;
    this.monoisotopicMass = formula.getMonoIsotopicMass();
    this.structure = null;
  }

  LcmsMetabolite(MolecularStructure structure) {
    this.structure = structure;
    this.monoisotopicMass = structure.getMonoIsotopicMass();
    this.formula = structure.getChemicalFormula();
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
