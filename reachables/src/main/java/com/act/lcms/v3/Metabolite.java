package com.act.lcms.v3;


import java.util.Map;
import java.util.Optional;

public interface Metabolite {
  Optional<MolecularStructure> getStructure();
  Optional<ChemicalFormula> getFormula();
  Double getMonoIsotopicMass();
  Map<Isotope, Double> getIsotopes();
}
