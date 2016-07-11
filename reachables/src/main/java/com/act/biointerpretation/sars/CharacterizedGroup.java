package com.act.biointerpretation.sars;

import java.util.Set;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {
  SeqGroup group;
  Sar sar;
  Set<Integer> ros;

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

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(group);
    builder.append(sar);
    builder.append(ros);
    return builder.toString();
  }

}
