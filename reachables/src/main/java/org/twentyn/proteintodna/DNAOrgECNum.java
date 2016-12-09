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

  /**
   * setOfOrganismAndEcNums is a set of sets since the following reason:
   * A dna seq is derived from a set of reactions protein sequences. A given reaction protein sequence can
   * have multiple (organism, ecnum) pairs associated to that protein sequence. Therefore, we need different sets to represent
   * the different (organism, ecnum) pairs of the reaction in the pathway. This is why setOfOrganismAndEcNums is a set
   * of sets.
   */
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
