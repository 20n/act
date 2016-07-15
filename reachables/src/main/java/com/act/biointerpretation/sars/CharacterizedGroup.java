package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
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

  @JsonProperty("ro")
  Ero ro;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  public CharacterizedGroup(SeqGroup group, List<Sar> sars, Ero ro) {
    this.group = group;
    this.sars = sars;
    this.ro = ro;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public SeqGroup getGroup() {
    return group;
  }

  public Ero getRo() {
    return ro;
  }
}
