package act.installer.reachablesexplorer;

public class PhysiochemicalProperties {

  public Double getPKA_ACID_1() {
    return PKA_ACID_1;
  }

  public void setPKA_ACID_1(Double PKA_ACID_1) {
    this.PKA_ACID_1 = PKA_ACID_1;
  }

  public Double getLOGP_TRUE() {
    return LOGP_TRUE;
  }

  public void setLOGP_TRUE(Double LOGP_TRUE) {
    this.LOGP_TRUE = LOGP_TRUE;
  }

  public Double getHLB_VAL() {
    return HLB_VAL;
  }

  public void setHLB_VAL(Double HLB_VAL) {
    this.HLB_VAL = HLB_VAL;
  }

  private Double PKA_ACID_1;
  private Double LOGP_TRUE;
  private Double HLB_VAL;

  public PhysiochemicalProperties(Double pka, Double logp, Double hlb) {
    this.PKA_ACID_1 = pka;
    this.LOGP_TRUE = logp;
    this.HLB_VAL = hlb;
  }


}
