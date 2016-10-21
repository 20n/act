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
   * @return a mapping {trace id -> peak spectrum}
   */
  Map<String, PeakSpectrum> getAllPeaks();

  /**
   * Retrieve all peaks related to a given trace
   * @param traceId the string id of a trace
   * @return the peak spectrum corresponding to the trace
   */
  PeakSpectrum getPeaks(String traceId);

  /**
   * Retrieve all peaks from the combined spectrum, around a mass, with a certain confidence level
   * @param mass the target mass (in Da)
   * @param confidenceLevel the confidence level (for example 0.05)
  * @return a mapping {trace id -> peak spectrum} of matching peaks
   */
  Map<String, PeakSpectrum> getPeaks(Double mass, Double confidenceLevel);

  /**
   * Get the trace ids of underlying traces
   */
  List<String> getTraceIds();
}
