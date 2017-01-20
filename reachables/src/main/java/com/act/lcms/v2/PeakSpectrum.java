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

  /*
   * Retrieval of peaks around a given mz and/or time value, with a certain confidence level
   */
  List<DetectedPeak> getPeaksByMZ(Double mz, Double confidenceLevel);
  List<DetectedPeak> getPeaksByTime(Double time, Double confidenceLevel);
  List<DetectedPeak> getPeaksByMzTime(Double time, Double mz, Double confidenceLevel);

  /*
   * Retrieval of peaks around a given mz and/or time value, with a given mz/time tolerance
   */
  List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double mzTolerance, Double timeTolerance);
  List<DetectedPeak> getNeighborhoodPeaks(Double mz, Double mzTolerance, Double time, Double timeTolerance);

  /**
   * Partition the spectrum by scan file id.
   * @return a mapping between scan files and their corresponding detected peaks.
   */
  Map<String, List<DetectedPeak>> getPeaksByScanFile();

  /**
   * Extract a list of peaks corresponding to a given scan file
   * @param scanFileId id of the desired scan file
   * @return the corresponding PeakSpectrum
   */
  List<DetectedPeak> getPeaks(String scanFileId);

}
