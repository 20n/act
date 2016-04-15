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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ROTestCase that = (ROTestCase) o;

    if (input != null ? !input.equals(that.input) : that.input != null) return false;
    if (expected != null ? !expected.equals(that.expected) : that.expected != null) return false;
    return label != null ? label.equals(that.label) : that.label == null;

  }

  @Override
  public int hashCode() {
    int result = input != null ? input.hashCode() : 0;
    result = 31 * result + (expected != null ? expected.hashCode() : 0);
    result = 31 * result + (label != null ? label.hashCode() : 0);
    return result;
  }
}
