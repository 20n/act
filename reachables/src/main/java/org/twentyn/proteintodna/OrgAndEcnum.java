package org.twentyn.proteintodna;

public class OrgAndEcnum {

  public String getOrganism() {
    return organism;
  }

  public void setOrganism(String organism) {
    this.organism = organism;
  }

  public String getEcnum() {
    return ecnum;
  }

  public void setEcnum(String ecnum) {
    this.ecnum = ecnum;
  }

  private String organism;
  private String ecnum;

  public OrgAndEcnum(String organism, String ecum) {
    this.organism = organism;
    this.ecnum = ecum;
  }
}
