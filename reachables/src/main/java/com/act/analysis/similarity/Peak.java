package com.act.analysis.similarity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Peak {

  @JsonProperty("matching_inchis")
  private Double matchingInchis;

  @JsonProperty("matching_formulae")
  private Double matchingFormulae;

  @JsonProperty("moleculeMass")
  private Double moleculeMass;

  @JsonProperty("rank_metric")
  private Double rankMetric;

  @JsonProperty("rt")
  private Double retentionTime;

  @JsonProperty("mz_band")
  private Double mzBand;

  @JsonProperty("mz")
  private Double massCharge;

  @JsonProperty("rt_band")
  private Double rtBand;

  public Double getMatchingInchis() {
    return matchingInchis;
  }

  public void setMatchingInchis(Double matchingInchis) {
    this.matchingInchis = matchingInchis;
  }

  public Double getMatchingFormulae() {
    return matchingFormulae;
  }

  public void setMatchingFormulae(Double matchingFormulae) {
    this.matchingFormulae = matchingFormulae;
  }

  public Double getMoleculeMass() {
    return moleculeMass;
  }

  public void setMoleculeMass(Double moleculeMass) {
    this.moleculeMass = moleculeMass;
  }

  public Double getRankMetric() {
    return rankMetric;
  }

  public void setRankMetric(Double rankMetric) {
    this.rankMetric = rankMetric;
  }

  public Double getRetentionTime() {
    return retentionTime;
  }

  public void setRetentionTime(Double retentionTime) {
    this.retentionTime = retentionTime;
  }

  public Double getMzBand() {
    return mzBand;
  }

  public void setMzBand(Double mzBand) {
    this.mzBand = mzBand;
  }

  public Double getMassCharge() {
    return massCharge;
  }

  public void setMassCharge(Double massCharge) {
    this.massCharge = massCharge;
  }

  public Double getRtBand() {
    return rtBand;
  }

  public void setRtBand(Double rtBand) {
    this.rtBand = rtBand;
  }

  public Peak(Double matchingInchis, Double matchingFormulae, Double moleculeMass, Double rankMetric,
              Double retentionTime, Double mzBand, Double massCharge, Double rtBand) {
    this.matchingInchis = matchingInchis;
    this.matchingFormulae = matchingFormulae;
    this.moleculeMass = moleculeMass;
    this.rankMetric = rankMetric;
    this.mzBand = mzBand;
    this.retentionTime = retentionTime;
    this.massCharge = massCharge;
    this.rtBand = rtBand;
  }



}
