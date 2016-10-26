package com.act.lcms.v2;


import chemaxon.struc.PeriodicSystem;

import java.util.ArrayList;
import java.util.List;

public class LcmsElement implements Element {

  private String symbol;
  private Integer atomicNumber;
  private Double atomicMass;
  private Integer valency;

  public LcmsElement(String symbol) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
  }

  public LcmsElement(String symbol, int atomicNumber) {
    this.symbol = symbol;
    this.atomicNumber = atomicNumber;
  }

  public LcmsElement(String symbol, Double atomicMass, Integer valency) {
    this.symbol = symbol;
    this.atomicMass = atomicMass;
    this.valency = valency;
  }

  @Override
  public String getSymbol() {
    return this.symbol;
  }

  @Override
  public Integer getAtomicNumber() {
    return this.atomicNumber;
  }

  @Override
  public Double getAtomicMass() {
    return this.atomicMass;
  }

  @Override
  public List<LcmsElementIsotope> getElementIsotopes() {
    Integer isotopeCount = PeriodicSystem.getIsotopeCount(atomicNumber);
    List<LcmsElementIsotope> elementIsotopes = new ArrayList<>(isotopeCount);

    for (int i = 0; i < isotopeCount; i++) {
      Integer massNumber = PeriodicSystem.getIsotope(atomicNumber, i);
      Double abundance = PeriodicSystem.getAbundance(atomicNumber, massNumber);
      elementIsotopes.add(new LcmsElementIsotope(massNumber, this, abundance));
    }

    return elementIsotopes;
  }

  @Override
  public Integer getValency() {
    return this.valency;
  }

}
