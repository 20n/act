package com.act.lcms.v2;


public class LcmsElementIsotope implements ElementIsotope {
  private Integer massNumber;
  private Element<LcmsElementIsotope> element;
  private Double abundance;

  public LcmsElementIsotope(Integer massNumber, Element<LcmsElementIsotope> element, Double abundance) {
    this.massNumber = massNumber;
    this.element = element;
    this.abundance = abundance;
  }

  @Override
  public Integer getMassNumber() {
    return massNumber;
  }

  @Override
  public Element<LcmsElementIsotope> getElement() {
    return element;
  }

  @Override
  public Double getAbundance() {
    return abundance;
  }
}
