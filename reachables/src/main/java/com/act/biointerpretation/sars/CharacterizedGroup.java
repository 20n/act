package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup implements Serializable {

  @JsonProperty("reaction_group_name")
  private String groupName;

  @JsonProperty("sars")
  private List<Sar> sars;

  @JsonProperty("reactor")
  private SerializableReactor reactor;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  public CharacterizedGroup(String groupName, List<Sar> sars, SerializableReactor reactor) {
    this.groupName = groupName;
    this.sars = sars;
    this.reactor = reactor;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public String getGroupName() {
    return groupName;
  }

  public SerializableReactor getReactor() {
    return reactor;
  }

  private void setReactor(SerializableReactor reactor) {
    this.reactor = reactor;
  }
}
