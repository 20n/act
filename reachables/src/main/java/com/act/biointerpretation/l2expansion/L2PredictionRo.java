package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by gil on 6/15/16.
 */
public class L2PredictionRo {

  @JsonProperty("_id")
  private Integer id;

  @JsonProperty("reaction_rule")
  private String reactionRule;

  private L2PredictionRo() {
  }

  public L2PredictionRo(Integer id, String reactionRule) {
    this.id = id;
    this.reactionRule = reactionRule;
  }

  public Integer getId() {
    return id;
  }

  public String getReactionRule() {
    return reactionRule;
  }
}
