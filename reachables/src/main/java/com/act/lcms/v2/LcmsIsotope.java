package com.act.lcms.v2;

public class LcmsIsotope implements Isotope {
  private Metabolite metabolite;
  private Double isotopicMass;
  private Double abundance;

  public LcmsIsotope(Metabolite metabolite, Double isotopicMass, Double abundance) {
    this.metabolite = metabolite;
    this.isotopicMass = isotopicMass;
    this.abundance = abundance;
  }

  @Override
  public Metabolite getMetabolite() {
    return metabolite;
  }

  @Override
  public Double getIsotopicMass() {
    return isotopicMass;
  }

  @Override
  public Double getAbundance() {
    return abundance;
  }
}
