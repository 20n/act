package act.installer.reachablesexplorer;

public class PhysiochemicalProperties {
  private Double pkaAcid1;
  private Double logPTrue;
  private Double hlbVal;

  public PhysiochemicalProperties() {}

  public PhysiochemicalProperties(Double pka, Double logp, Double hlb) {
    this.pkaAcid1 = pka;
    this.logPTrue = logp;
    this.hlbVal = hlb;
  }

  public Double getPkaAcid1() {
    return pkaAcid1;
  }

  public void setPkaAcid1(Double pkaAcid1) {
    this.pkaAcid1 = pkaAcid1;
  }

  public Double getLogPTrue() {
    return logPTrue;
  }

  public void setLogPTrue(Double logPTrue) {
    this.logPTrue = logPTrue;
  }

  public Double getHlbVal() {
    return hlbVal;
  }

  public void setHlbVal(Double hlbVal) {
    this.hlbVal = hlbVal;
  }
}
