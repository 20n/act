package com.act.lcms.v3;

/**
 * Created by tom on 10/19/16.
 */
public class FixedWindowDetectedPeak implements DetectedPeak {

  private String sourceTraceId;
  private Double mz;
  private Double mzWindow;
  private Double retentionTime;
  private Double retentionTimeWindow;
  private Double intensity;
  private Double confidence;


  @Override
  public Boolean matches(Float mz, Double confidence) {
    return this.mz - this.mzWindow / 2 <= mz && this.mz + this.mzWindow / 2 >= mz;
  }

  @Override
  public Double getMz() {
    return mz;
  }

  @Override
  public Double getRetentionTime() {
    return retentionTime;
  }

  @Override
  public Double getIntensity() {
    return intensity;
  }

  @Override
  public String getSourceTraceId() {
    return sourceTraceId;
  }

  @Override
  public Double getConfidence() {
    return confidence;
  }
}
