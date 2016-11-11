package com.act.lcms.v2;

import java.util.Optional;

public class LcmsMetabolite implements Metabolite {

  private MolecularStructure structure;
  private ChemicalFormula formula;
  private Double monoisotopicMass;

  public LcmsMetabolite(Double monoisotopicMass) {
    this(null, null, monoisotopicMass);
  }

  public LcmsMetabolite(ChemicalFormula formula) {
    this(null, formula, formula.getMonoIsotopicMass());
  }

  public LcmsMetabolite(MolecularStructure structure) {
    this(structure, structure.getChemicalFormula(), structure.getMonoIsotopicMass());
  }

  private LcmsMetabolite(MolecularStructure structure, ChemicalFormula formula, Double monoisotopicMass) {
    this.structure = structure;
    this.formula = formula;
    this.monoisotopicMass = monoisotopicMass;
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
