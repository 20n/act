package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class LabelledReaction {

  @JsonProperty("ecnum")
  private String ecnum;

  @JsonProperty("notes")
  private String notes;

  @JsonProperty("easy_desc")
  private String easy_desc;

  @JsonProperty("products")
  private Set<String> products;

  @JsonProperty("substrates")
  private Set<String> substrates;

  public String getEcnum() {
    return ecnum;
  }

  public void setEcnum(String ecnum) {
    this.ecnum = ecnum;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getEasy_desc() {
    return easy_desc;
  }

  public void setEasy_desc(String easyDesc) {
    this.easy_desc = easyDesc;
  }

  public Set<String> getProducts() {
    return products;
  }

  public void setProducts(Set<String> products) {
    this.products = products;
  }

  public Set<String> getSubstrates() {
    return substrates;
  }

  public void setSubstrates(Set<String> substrates) {
    this.substrates = substrates;
  }
}
