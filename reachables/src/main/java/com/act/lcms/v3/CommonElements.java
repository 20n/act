package com.act.lcms.v3;

import chemaxon.struc.PeriodicSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by thomas on 10/20/16.
 */
public enum CommonElements implements Element {
  C(6), H(1), N, O, P, S,
  ;

  private String symbol;
  private Integer atomicNumber;

  CommonElements() {
    this.symbol = this.name();
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
  }

  CommonElements(int atomicNumber) {
    this.symbol = this.name();
    this.atomicNumber = atomicNumber;
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
  public List<ElementIsotope> getElementIsotopes() {
    return new ArrayList<>();
  }

  @Override
  public Integer getValency() {
    return -1;
  }
}
