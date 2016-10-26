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

  public void setMetabolite(Metabolite metabolite) {
    this.metabolite = metabolite;
  }

  @Override
  public Double getIsotopicMass() {
    return isotopicMass;
  }

  public void setIsotopicMass(Double isotopicMass) {
    this.isotopicMass = isotopicMass;
  }

  @Override
  public Double getAbundance() {
    return abundance;
  }

  public void setAbundance(Double abundance) {
    this.abundance = abundance;
  }
}
