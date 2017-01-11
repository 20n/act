package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

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

  // ToDo: Convert the argument parameter to a Collection if the deserialization is not de-duping entries correctly
  public void setDnaDesigns(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  @ObjectId
  @JsonProperty("_id")
  private String id;

  @JsonProperty("designs")
  private Set<DNAOrgECNum> dnaDesigns;

  public DNADesign(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  private DNADesign() {}
}
