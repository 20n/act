package com.act.lcms.v2;

import java.util.List;

/**
 * Interface representing supporting evidence for the presence of a Metabolite in an LCMS trace
 */
public interface MoleculeSupport {

  /**
   * Get the supported Metabolite
   */
  Metabolite getMetabolite();

  /**
   * Get the list of Ions supporting the presence of the Metabolite
   */
  List<Ion> getSupportedIons();

  /**
   * Return detected peaks supporting the presence of a particular ion
   * @param ion query Ion
   * @return a list of detected peaks, supporting the presence of that ion
   */
  List<DetectedPeak> getDetectedPeaks(Ion ion);
}
