package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.MolecularStructure;
import org.apache.commons.lang.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InchiStructure implements MolecularStructure {

  private static final Logger LOGGER = LogManager.getFormatterLogger(InchiStructure.class);

  private final String inchi;

  public InchiStructure(String inchi) {
    this.inchi = inchi;
  }

  @Override
  public String getInchi() {
    return inchi;
  }

  @Override
  public Double getMonoIsotopicMass() {
    try {
      return MassCalculator.calculateMass(inchi);
    } catch (Exception e) {
      return -1.0;
    }
  }

  @Override
  public ChemicalFormula getChemicalFormula() {
    throw new NotImplementedException();
  }
}

