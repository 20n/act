package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.MolecularStructure;
import com.ggasoftware.indigo.IndigoException;
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
  public Boolean parseInchi(String inchi) {
    throw new NotImplementedException();
  }

  @Override
  public Double getMass() {
    try {
      return MassCalculator.calculateMass(inchi);
    } catch (IndigoException e) {
      LOGGER.error("Couldn't calculate mass for metabolite %s: %s", inchi, e.getMessage());
      return -1.0;
    }
  }

  @Override
  public ChemicalFormula getChemicalFormula() {
    throw new NotImplementedException();
  }
}

