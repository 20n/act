package com.act.lcms.v2;


import org.apache.commons.lang.NotImplementedException;

import java.util.Optional;

public class LcmsMetabolite implements Metabolite {

  public Optional<MolecularStructure> getStructure() {
    throw new NotImplementedException();
  }

  public Optional<ChemicalFormula> getFormula() {
    throw new NotImplementedException();
  }

  public Double getMonoIsotopicMass() {
    throw new NotImplementedException();
  }

}
