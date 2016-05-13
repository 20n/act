package com.act.biointerpretation.desalting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DesaltingRO {

  @JsonProperty("description")
  private String description;

  @JsonProperty("reaction")
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

  public List<ROTestCase> getTestCases() {
    return testCases;
  }

  public void setTestCases(List<ROTestCase> test_cases) {
    this.testCases = test_cases;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DesaltingRO that = (DesaltingRO) o;

    if (description != null ? !description.equals(that.description) : that.description != null) return false;
    if (reaction != null ? !reaction.equals(that.reaction) : that.reaction != null) return false;
    return testCases != null ? testCases.equals(that.testCases) : that.testCases == null;

  }

  @Override
  public int hashCode() {
    int result = description != null ? description.hashCode() : 0;
    result = 31 * result + (reaction != null ? reaction.hashCode() : 0);
    result = 31 * result + (testCases != null ? testCases.hashCode() : 0);
    return result;
  }
}
