package com.act.lcms.v3;

import java.util.List;
import java.util.Map;

public interface CombinedSpectrum {
  Map<String, PeakSpectrum> getAllPeaks();
  PeakSpectrum getPeaks(String traceId);
  Map<String, PeakSpectrum> getPeaks(Float mass, Float confidenceInterval);
  List<String> getTraceIds();
}
