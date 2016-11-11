package com.act.lcms.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsSpectrum implements PeakSpectrum {

  List<DetectedPeak> peaks;

  public LcmsSpectrum(List<DetectedPeak> peaks) {
    this.peaks = peaks;
  }

  public LcmsSpectrum(PeakSpectrum spectrum) {
    this(spectrum.getAllPeaks());
  }

  public LcmsSpectrum() {
    this(new ArrayList<>());
  }

  public void addPeak(DetectedPeak peak) {
    peaks.add(peak);
  }

  @Override
  public List<DetectedPeak> getAllPeaks() {
    return peaks;
  }

  @Override
  public List<DetectedPeak> getPeaks(Predicate<DetectedPeak> filter) {
    return peaks.stream().filter(filter).collect(Collectors.toList());
  }

  @Override
  public List<DetectedPeak> getPeaksByMZ(Double mz, Double confidenceLevel) {
    return getPeaks(peak -> peak.matchesMz(mz, confidenceLevel));
  }

  @Override
  public List<DetectedPeak> getPeaksByTime(Double time, Double timeTolerance) {
    return getPeaks(peak -> Math.abs(peak.getRetentionTime() - time) <= timeTolerance);
  }

  @Override
  public List<DetectedPeak> getPeaksByMzTime(Double time, Double mz, Double confidenceLevel) {
    return getPeaks(peak -> peak.matchesMzTime(mz, time, confidenceLevel));
  }

  @Override
  public List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double massTolerance, Double timeTolerance) {
    return getNeighborhoodPeaks(targetPeak.getMz(), massTolerance, targetPeak.getRetentionTime(), timeTolerance);
  }

  @Override
  public List<DetectedPeak> getNeighborhoodPeaks(Double mass, Double massTolerance, Double time, Double timeTolerance) {
    return getPeaks(peak -> Math.abs(peak.getRetentionTime() - time) < timeTolerance &&
        Math.abs(peak.getMz() - mass) < massTolerance);
  }

  @Override
  public Map<String, List<DetectedPeak>> getPeaksByScanFile() {
    return peaks.stream().collect(Collectors.groupingBy(DetectedPeak::getSourceScanFileId));
  }

  @Override
  public List<DetectedPeak> getPeaks(String scanFileId) {
    return getPeaks(peak -> peak.getSourceScanFileId().equals(scanFileId));
  }
}
