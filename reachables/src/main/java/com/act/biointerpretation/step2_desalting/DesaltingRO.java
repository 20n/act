package com.act.biointerpretation.step2_desalting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

public class DesaltingRO {

  private String description;
  private String reaction;

  @JsonProperty("test_cases")
  private List<ROTestCase> testCases;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getReaction() {
    return reaction;
  }

  public void setReaction(String reaction) {
    this.reaction = reaction;
  }

  @JsonGetter("test_cases")
  public List<ROTestCase> getTestCases() {
    return testCases;
  }

  @JsonSetter("test_cases")
  public void setTestCases(List<ROTestCase> test_cases) {
    this.testCases = test_cases;
  }
}
