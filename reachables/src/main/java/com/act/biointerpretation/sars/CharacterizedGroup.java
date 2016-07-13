package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {

  @JsonProperty("seq_group")
  SeqGroup group;

  @JsonProperty("sars")
  List<Sar> sars;

  @JsonProperty("ros")
  Set<Integer> ros;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  public CharacterizedGroup(SeqGroup group, List<Sar> sars, Set<Integer> ros) {
    this.group = group;
    this.sars = sars;
    this.ros = ros;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public SeqGroup getGroup() {
    return group;
  }

  public Set<Integer> getRos() {
    return ros;
  }
}
