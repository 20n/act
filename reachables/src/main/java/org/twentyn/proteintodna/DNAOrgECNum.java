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

  public Set<Set<OrgAndEcnum>> getSetOfOrganismAndEcNums() {
    return setOfOrganismAndEcNums;
  }

  public void setSetOfOrganismAndEcNums(Set<Set<OrgAndEcnum>> setOfOrganismAndEcNums) {
    this.setOfOrganismAndEcNums = setOfOrganismAndEcNums;
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
  Set<Set<OrgAndEcnum>> setOfOrganismAndEcNums;

  @JsonProperty("num_proteins")
  private Integer numProteins;

  public DNAOrgECNum(String dna, Set<Set<OrgAndEcnum>> setOfOrganismAndEcNums, Integer numProteins) {
    this.dna = dna;
    this.setOfOrganismAndEcNums = setOfOrganismAndEcNums;
    this.numProteins = numProteins;
  }
}
