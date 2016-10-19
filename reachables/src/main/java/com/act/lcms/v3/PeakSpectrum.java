package com.act.lcms.v3;

import java.util.List;
import java.util.function.Predicate;

public interface PeakSpectrum {
  List<DetectedPeak> getAllPeaks();
  List<DetectedPeak> getPeaks(Predicate<DetectedPeak> filter); // generic API, supports other getPeaks() methods
  List<DetectedPeak> getPeaksByMass(Double mass, Double confidence);
  List<DetectedPeak> getPeaksByTime(Double time, Double confidenceInterval);
  List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double massTolerance, Double timeTolerance);
  List<DetectedPeak> getNeighborhoodPeaks(Double mass, Double massTolerance, Double time, Double timeTolerance);
}
