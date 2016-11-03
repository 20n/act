package com.act.lcms.v2;


import chemaxon.struc.PeriodicSystem;

import java.util.ArrayList;
import java.util.List;

public class LcmsElement implements Element {

  // Chemaxon's API for PeriodicSystem returns isotopes that have abundance 0.
  // We need to filter these out by setting a minimum abundance level.
  // Abundance ranges from 0-100 with the most abundant isotope having an abundance of 100
  private static final Double MIN_ABUNDANCE = 0.1;

  private String symbol;
  private Integer atomicNumber;
  private Double mass;
  private Integer valency;

  public LcmsElement(String symbol) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
  }

  public LcmsElement(Integer atomicNumber) {
    this.atomicNumber = atomicNumber;
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
  }

  public LcmsElement(String symbol, Integer valency) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
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
  public Double getMass() {
    return this.mass;
  }

  @Override
  public List<ElementIsotope> getElementIsotopes() {
    Integer isotopeCount = PeriodicSystem.getIsotopeCount(atomicNumber);
    List<ElementIsotope> elementIsotopes = new ArrayList<>(isotopeCount);

    for (int i = 0; i < isotopeCount; i++) {
      Integer massNumber = PeriodicSystem.getIsotope(atomicNumber, i);
      Double abundance = PeriodicSystem.getAbundance(atomicNumber, massNumber);
      if (abundance > MIN_ABUNDANCE) {
        elementIsotopes.add(new LcmsElementIsotope(this, massNumber));
      }
    }

    return elementIsotopes;
  }

  @Override
  public Integer getValency() {
    return this.valency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Element that = (Element) o;

    if (!symbol.equals(that.getSymbol())) return false;
    return atomicNumber.equals(that.getAtomicNumber());
  }

  @Override
  public int hashCode() {
    int result = symbol.hashCode();
    result = 31 * result + atomicNumber.hashCode();
    return result;
  }
}
