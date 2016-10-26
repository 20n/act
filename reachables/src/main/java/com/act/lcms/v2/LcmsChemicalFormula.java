package com.act.lcms.v2;

import org.apache.commons.lang.NotImplementedException;

import java.util.Map;
import java.util.Optional;


public class LcmsChemicalFormula implements ChemicalFormula<LcmsElement> {

  private Map<LcmsElement, Integer> elementCounts;

  public Map<LcmsElement, Integer> getElementCounts() {
    return this.elementCounts;
  }

  public Integer getElementCount(LcmsElement element) {
    return elementCounts.getOrDefault(element, 0);
  }

  public Double getMonoIsotopicMass() {
    return elementCounts
        .entrySet()
        .stream()
        .mapToDouble(entry -> entry.getKey().getAtomicMass() * entry.getValue())
        .sum();
  }

  public Optional<String> getName() {
    return null;
  }

  public Boolean equals(ChemicalFormula chemicalFormula) {
    return getElementCounts().equals(chemicalFormula.getElementCounts());
  }

  public String toString() {
    throw new NotImplementedException();
  }
}
