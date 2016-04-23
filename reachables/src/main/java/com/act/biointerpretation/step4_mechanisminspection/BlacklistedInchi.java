package com.act.biointerpretation.step4_mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlacklistedInchi {

  @JsonProperty("wrong_inchi")
  private String wrong_inchi;

  @JsonProperty("correct_inchi")
  private String correct_inchi;

  public String getWrong_inchi() {
    return wrong_inchi;
  }

  public BlacklistedInchi() {}

  public void setWrong_inchi(String wrong_inchi) {
    this.wrong_inchi = wrong_inchi;
  }

  public String getCorrect_inchi() {
    return correct_inchi;
  }

  public void setCorrect_inchi(String correct_inchi) {
    this.correct_inchi = correct_inchi;
  }
}
