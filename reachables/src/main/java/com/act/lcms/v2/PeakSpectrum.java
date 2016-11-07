package com.act.lcms.v2;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Interface representing a collection of detected peaks.
 * Example use case: representation of the output of a peak calling algorithm
 */
public interface PeakSpectrum {

  /**
   * Get all detected peaks in the spectrum
   */
  List<DetectedPeak> getAllPeaks();

  /**
   * Get all peaks in the spectrum that satisfy a given predicate
   * @param filter input Predicate
   * @return a list of detectedpeaks
   */
  List<DetectedPeak> getPeaks(Predicate<DetectedPeak> filter); // generic API, supports other getPeaks() methods

  /*
   * All following APIs are supported by the above getPeaks.
   */
  List<DetectedPeak> getPeaksByMass(Double mass, Double massTolerance);
  List<DetectedPeak> getPeaksByTime(Double time, Double timeTolerance);
  List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double massTolerance, Double timeTolerance);
  List<DetectedPeak> getNeighborhoodPeaks(Double mass, Double massTolerance, Double time, Double timeTolerance);

  /**
   * Partition the spectrum by scan file id.
   * @return a mapping between scan files and their corresponding detected peaks.
   */
  Map<String, PeakSpectrum> getPeakSpectraByScanFile();

  /**
   * Extract a spectrum corresponding to a given scan file
   * @param scanFileId id of the desired scan file
   * @return the corresponding PeakSpectrum
   */
  PeakSpectrum getSpectrum(String scanFileId);

}
