package com.act.lcms.v3;

public interface DetectedPeak {
  Double getMz();
  Double getRetentionTime();
  Double getIntensity();
  String getSourceTraceId();
  Double getConfidence();
  Boolean matches(Float mz, Double confidence);
}
