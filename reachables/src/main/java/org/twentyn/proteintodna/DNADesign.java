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

  public Set<String> getDnaDesigns() {
    return dnaDesigns;
  }

  public void setDnaDesigns(Set<String> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  @ObjectId
  @JsonProperty("_id")
  private String id;

  @JsonProperty("designs")
  private Set<String> dnaDesigns;

  private DNADesign() {

  }

  public DNADesign(Set<String> dnaDesigns) {
      this.dnaDesigns = dnaDesigns;
    }
}
