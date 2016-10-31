package com.act.lcms.v2;

import org.apache.commons.lang.NotImplementedException;

import java.util.Map;
import java.util.Optional;


public class LcmsChemicalFormula implements ChemicalFormula {

  public LcmsChemicalFormula(Map<Element, Integer> elementCounts) {
    this.elementCounts = elementCounts;
  }

  public LcmsChemicalFormula(Map<Element, Integer> elementCounts, String name) {
    this.elementCounts = elementCounts;
    this.name = name;
  }

  private Map<Element, Integer> elementCounts;
  private String name;

  public Map<Element, Integer> getElementCounts() {
    return this.elementCounts;
  }

  public Integer getElementCount(Element element) {
    return elementCounts.getOrDefault(element, 0);
  }

  public Double getMonoIsotopicMass() {
    return elementCounts
        .entrySet()
        .stream()
        .mapToDouble(entry -> entry.getKey().getMass() * entry.getValue())
        .sum();
  }

  public Optional<String> getName() {
    return Optional.ofNullable(this.name);
  }

  public Boolean equals(ChemicalFormula chemicalFormula) {
    return getElementCounts().equals(chemicalFormula.getElementCounts());
  }

  public String toString() {
    throw new NotImplementedException();
  }
}
