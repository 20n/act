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

  public OrgAndEcnum() {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    OrgAndEcnum that = (OrgAndEcnum) o;

    if (organism != null ? !organism.equals(that.organism) : that.organism != null) return false;
    return ecnum != null ? ecnum.equals(that.ecnum) : that.ecnum == null;

  }

  @Override
  public int hashCode() {
    int result = organism != null ? organism.hashCode() : 0;
    result = 31 * result + (ecnum != null ? ecnum.hashCode() : 0);
    return result;
  }
}
