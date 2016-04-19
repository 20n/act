package com.act.biointerpretation.step3_cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Cofactor {

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("name")
  private String name;

  @JsonProperty("set")
  private String set;

  @JsonProperty("rank")
  private Integer rank;

  public Cofactor() {}

  public String getInchi() {
    return inchi;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSet() {
    return set;
  }

  public void setSet(String set) {
    this.set = set;
  }

  public Integer getRank() {
    return rank;
  }

  public void setRank(Integer rank) {
    this.rank = rank;
  }
}
