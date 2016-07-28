package com.act.lcms.db.analysis;

public class MoleculeAndItsMetlinIon {

  private String inchi;
  private Double massCharge;
  private String ion;

  public MoleculeAndItsMetlinIon(String inchi, Double massCharge, String ion) {
    this.inchi = inchi;
    this.massCharge = massCharge;
    this.ion = ion;
  }

  public String getInchi() {
    return inchi;
  }

  public Double getMassCharge() {
    return massCharge;
  }

  public String getIon() {
    return ion;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MoleculeAndItsMetlinIon)) return false;

    MoleculeAndItsMetlinIon that = (MoleculeAndItsMetlinIon) o;
    return this.getInchi().equals(that.getInchi()) &&
        this.getIon().equals(that.getIon()) &&
        this.getMassCharge().equals(that.getMassCharge());
  }

  @Override
  public int hashCode() {
    return this.getInchi().hashCode() ^ this.getIon().hashCode() ^ this.getMassCharge().hashCode();
  }
}
