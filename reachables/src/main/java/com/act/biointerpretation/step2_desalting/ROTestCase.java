package com.act.biointerpretation.step2_desalting;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ROTestCase {

  @JsonProperty("input")
  private String input;

  @JsonProperty("expected")
  private String expected;

  @JsonProperty("label")
  private String label;

  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }

  public String getExpected() {
    return expected;
  }

  public void setExpected(String expected) {
    this.expected = expected;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
