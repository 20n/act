package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {

  @JsonProperty("reaction_group")
  private ReactionGroup group;

  @JsonProperty("sars")
  private List<Sar> sars;

  @JsonProperty("reactor")
  private SerializableReactor reactor;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }


  public CharacterizedGroup(ReactionGroup group, List<Sar> sars, SerializableReactor reactor) {

    this.group = group;
    this.sars = sars;
    this.reactor = reactor;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public ReactionGroup getGroup() {
    return group;
  }

  public SerializableReactor getReactor() {
    return reactor;
  }

  private void setReactor(SerializableReactor reactor) {
    this.reactor = reactor;
  }
}
