package com.act.biointerpretation.step3_cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FakeCofactorMapping {

  @JsonProperty("fake_cofactor_name")
  private String fake_cofactor_name;

  @JsonProperty("cofactor_name")
  private String cofactor_name;

  @JsonProperty("rank")
  private Integer rank;

  public String getFake_cofactor_name() {
    return fake_cofactor_name;
  }

  public void setFake_cofactor_name(String fake_cofactor_name) {
    this.fake_cofactor_name = fake_cofactor_name;
  }

  public String getCofactor_name() {
    return cofactor_name;
  }

  public void setCofactor_name(String cofactor_name) {
    this.cofactor_name = cofactor_name;
  }

  public Integer getRank() {
    return rank;
  }

  public void setRank(Integer rank) {
    this.rank = rank;
  }
}
