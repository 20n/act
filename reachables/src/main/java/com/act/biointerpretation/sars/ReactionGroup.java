package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of reactions that has been deemed likely explicable by the same SAR.
 * Importantly a ReactionGroup has not yet been characterized by a particular SAR.
 */
public class ReactionGroup {

  @JsonProperty("name")
  private String name;

  @JsonProperty("reaction_ids")
  private Set<Long> reactionIds;

  /**
   * For JSON.
   */
  private ReactionGroup() {
  }

  public ReactionGroup(String name) {
    this.name = name;
    reactionIds = new HashSet<>();
  }

  public void addReactionId(Long id) {
    reactionIds.add(id);
  }

  public Collection<Long> getReactionIds() {
    return new ArrayList(reactionIds);
  }

  public String getName() {
    return name;
  }
}
