package com.act.lcms.v2;


import chemaxon.struc.PeriodicSystem;

public class LcmsElementIsotope implements ElementIsotope {
  private Integer massNumber;
  private Element element;
  private Double abundance;
  private Double isotopicMass;

  public LcmsElementIsotope(Element element, Integer massNumber) {
    this.element = element;
    this.massNumber = massNumber;
    this.abundance = PeriodicSystem.getAbundance(this.element.getAtomicNumber(), this.massNumber);
    this.isotopicMass = PeriodicSystem.getMass(this.element.getAtomicNumber(), this.massNumber);
  }

  @Override
  public Integer getMassNumber() {
    return massNumber;
  }

  @Override
  public Element getElement() {
    return element;
  }

  @Override
  public Double getAbundance() {
    return abundance;
  }

  @Override
  public Double getIsotopicMass() {
    return isotopicMass;
  }
}
