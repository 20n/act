package com.act.lcms.v2;


import chemaxon.struc.PeriodicSystem;

import java.util.ArrayList;
import java.util.List;


public enum CommonElements implements Element {
  CARBON("C", 12.000000, 4),
  HYDROGEN("H", 1.007825, 1),
  OXYGEN("O", 15.994915, 2),
  NITROGEN("N", 14.003074, 4),
  PHOSPHORUS("P", 30.973761, 2),
  SULFUR("S", 31.972071, 6),
  IODINE("I", 126.904457, 1),
  FLUORINE("F", 18.998404, 1),
  CHLORINE("Cl", 34.968853, 1),
  BROMINE("Br", 78.918327, 1),
  ;

  private String symbol;
  private Integer atomicNumber;
  private Double atomicMass;
  private Integer valency;

  CommonElements(String symbol) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
  }

  CommonElements(String symbol, int atomicNumber) {
    this.symbol = symbol;
    this.atomicNumber = atomicNumber;
  }

  CommonElements(String symbol, Double atomicMass, Integer valency) {
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
  public List<ElementIsotope> getElementIsotopes() {
    return new ArrayList<>();
  }

  @Override
  public Integer getValency() {
    return this.valency;
  }
}
