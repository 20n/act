package com.act.lcms.v3;

import java.util.Map;
import java.util.Optional;

public interface ChemicalFormula {
  Map<Atom, Integer> getAtomCounts();
  Integer getAtomCount(Atom atom);
  Double getMonoIsotopicMass();
  Boolean matchesMolecularStructure(MolecularStructure structure);
  Optional<String> getName();
}
