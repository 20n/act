package com.act.biointerpretation.sars;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of sequences that has been deemed likely explicable by the same SAR, based on their sequences
 * and reactions.  Importantly a SeqGroup has not yet been characterized by a particular SAR.
 */
public class SeqGroup {

  private Set<Long> seqIds;
  private Set<Long> reactionIds;
  private String sequence;

  public SeqGroup(String sequence) {
    this.sequence = sequence;
    seqIds = new HashSet<>();
    reactionIds = new HashSet<>();
  }

  public void addReactionId(Long id) {
    reactionIds.add(id);
  }

  public void addSeqId(Long id) {
    seqIds.add(id);
  }

  public Collection<Long> getReactionids() {
    return reactionIds;
  }

  public Collection<Long> getSeqIds() {
    return seqIds;
  }

  @Override
  public int hashCode() {
    return sequence.hashCode();
  }
}
