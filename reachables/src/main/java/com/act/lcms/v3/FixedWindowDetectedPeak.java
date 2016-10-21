package com.act.lcms.v3;

/**
 * Implementation of a fixed window peak.
 * Such a peak matches a m/z value if it falls within the m/z window around the peak.
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

  @Override
  public Boolean matches(Double mz, Double confidenceLevel) {
    // Matches if the m/z value is in the m/z window, regardless of the confidenceLevel
    return (this.mz - this.mzWindow / 2 <= mz) && (this.mz + this.mzWindow / 2 >= mz);
  }
}
