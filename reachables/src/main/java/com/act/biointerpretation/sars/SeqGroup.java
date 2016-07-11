package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of sequences that has been deemed likely explicable by the same SAR, based on their sequences
 * and reactions.  Importantly a SeqGroup has not yet been characterized by a particular SAR.
 */
public class SeqGroup {

  @JsonProperty("seq_ids")
  private Set<Integer> seqIds;

  @JsonProperty("reaction_ids")
  private Set<Long> reactionIds;

  @JsonProperty("sequence")
  private String sequence;

  /**
   * For JSON.
   */
  private SeqGroup() {
  }

  public SeqGroup(String sequence) {
    this.sequence = sequence;
    seqIds = new HashSet<>();
    reactionIds = new HashSet<>();
  }

  public void addReactionId(Long id) {
    reactionIds.add(id);
  }

  public void addSeqId(Integer id) {
    seqIds.add(id);
  }

  public Collection<Long> getReactionIds() {
    return reactionIds;
  }

  public Collection<Integer> getSeqIds() {
    return seqIds;
  }

  public String getSequence() {
    return sequence;
  }
}
