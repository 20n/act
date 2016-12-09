package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class DNADesign {

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Set<DNAOrgECNum> getDnaDesigns() {
    return dnaDesigns;
  }

  public void setDnaDesigns(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  @JsonProperty("_id")
  private String id;

  @JsonProperty("designs")
  private Set<DNAOrgECNum> dnaDesigns;

  public DNADesign(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }
}
