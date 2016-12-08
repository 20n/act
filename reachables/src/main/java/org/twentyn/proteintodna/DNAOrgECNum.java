package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class DNAOrgECNum {

  public String getDna() {
    return dna;
  }

  public void setDna(String dna) {
    this.dna = dna;
  }

  public Set<Set<OrgAndEcnum>> getOrgAndEcNum() {
    return orgAndEcNum;
  }

  public void setOrgAndEcNum(Set<Set<OrgAndEcnum>> orgAndEcNum) {
    this.orgAndEcNum = orgAndEcNum;
  }

  public Integer getNumProteins() {
    return numProteins;
  }

  public void setNumProteins(Integer numProteins) {
    this.numProteins = numProteins;
  }

  @JsonProperty("dna")
  String dna;

  @JsonProperty("org_ec")
  Set<Set<OrgAndEcnum>> orgAndEcNum;

  @JsonProperty("num_proteins")
  private Integer numProteins;

  public DNAOrgECNum(String dna, Set<Set<OrgAndEcnum>> orgAndEcNum, Integer proteins) {
    this.dna = dna;
    this.orgAndEcNum = orgAndEcNum;
    this.numProteins = proteins;
  }
}
