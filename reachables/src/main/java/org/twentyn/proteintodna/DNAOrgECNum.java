package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public class DNAOrgECNum {

  public String getDna() {
    return dna;
  }

  public void setDna(String dna) {
    this.dna = dna;
  }

  public Set<List<String>> getOrgAndEcNum() {
    return orgAndEcNum;
  }

  public void setOrgAndEcNum(Set<List<String>> orgAndEcNum) {
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
  Set<List<String>> orgAndEcNum;

  @JsonProperty("num_proteins")
  private Integer numProteins;

  public DNAOrgECNum(String dna, Set<List<String>> orgAndEcNum, Integer proteins) {
    this.dna = dna;
    this.orgAndEcNum = orgAndEcNum;
    this.numProteins = proteins;
  }
}
