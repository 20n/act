package com.act.lcms.v2;


public enum LcmsCommonElements {
  CARBON("C", 4),
  HYDROGEN("H", 1),
  OXYGEN("O", 2),
  NITROGEN("N", 4),
  PHOSPHORUS("P", 2),
  SULFUR("S", 6),
  IODINE("I", 1),
  FLUORINE("F", 1),
  CHLORINE("Cl", 1),
  BROMINE("Br", 1),
  ;

  private LcmsElement element;

  LcmsCommonElements(String symbol, Integer valency) {
    element = new LcmsElement(symbol, valency);
  }

  public LcmsElement getElement() {
    return this.element;
  }
}
