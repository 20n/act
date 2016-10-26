package com.act.lcms.v2;


public enum LcmsCommonElements {
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

  private LcmsElement element;

  LcmsCommonElements(String symbol, Double atomicMass, Integer valency) {
    new LcmsElement(symbol, atomicMass, valency);
  }
}
