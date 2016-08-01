package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Represents the RO portion of an L2Prediction.
 */
public class L2PredictionRo implements Serializable {
  private static final long serialVersionUID = -4242452694476880709L;

  @JsonProperty("_id")
  private Integer id;

  @JsonProperty("reaction_rule")
  private String reactionRule;

  // For json reading.
  private L2PredictionRo() {
  }

  public L2PredictionRo(L2PredictionRo template) {
    this.id = template.id;
    this.reactionRule = template.reactionRule;
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
