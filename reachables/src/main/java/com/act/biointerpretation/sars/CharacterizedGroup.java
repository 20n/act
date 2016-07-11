package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {

  @JsonProperty("seq_group")
  SeqGroup group;

  @JsonProperty("sar")
  Sar sar;

  @JsonProperty("ros")
  Set<Integer> ros;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  ;

  public CharacterizedGroup(SeqGroup group, Sar sar, Set<Integer> ros) {
    this.group = group;
    this.sar = sar;
    this.ros = ros;
  }

  public Sar getSar() {
    return sar;
  }

  public SeqGroup getGroup() {
    return group;
  }
}
