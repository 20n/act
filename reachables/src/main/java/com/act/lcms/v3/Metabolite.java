package com.act.lcms.v3;


import java.util.List;
import java.util.Optional;

public interface Metabolite {
  Optional<MolecularStructure> getStructure();
  Optional<ChemicalFormula> getFormula();
  Double getMonoIsotopicMass();
  List<Isotope> getIsotopes();
}
