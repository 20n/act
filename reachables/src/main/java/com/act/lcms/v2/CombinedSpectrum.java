package com.act.lcms.v2;

import java.util.List;
import java.util.Map;

/**
 * An interface representing a combined peak spectrum.
 * A combined spectrum is defined as a collection of peak spectrum, for example from replicate samples and
 * would typically come as input for a replicate combination algorithm.
 */
public interface CombinedSpectrum {
  /**
   * Retrieve all peaks from the combined spectrum
   * @return a mapping {scan file id -> peak spectrum}
   */
  Map<String, PeakSpectrum> getAllPeaks();

  /**
   * Retrieve all peaks related to a given scan file
   * @param scanFileId the string id of a scan file
   * @return the peak spectrum corresponding to the scan file
   */
  PeakSpectrum getPeaks(String scanFileId);

  /**
   * Retrieve all peaks from the combined spectrum, around a mass, with a certain confidence level
   * @param mass the target mass (in Da)
   * @param confidenceLevel the confidence level (for example 0.05)
  * @return a mapping {scan file id -> peak spectrum} of matching peaks
   */
  Map<String, PeakSpectrum> getPeaks(Double mass, Double confidenceLevel);

  /**
   * Get the scan file ids
   */
  List<String> getScanFileIds();
}
