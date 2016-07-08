package com.act.biointerpretation.sars;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {
  SeqGroup group;
  Sar sar;

  public CharacterizedGroup(SeqGroup group, Sar sar) {
    this.group = group;
    this.sar = sar;
  }

  public Sar getSar() {
    return sar;
  }

  public SeqGroup getGroup() {
    return group;
  }
}
