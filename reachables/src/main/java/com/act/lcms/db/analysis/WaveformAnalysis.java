package com.act.lcms.db.analysis;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WaveformAnalysis {
  /**
   * This function checks if there are overlaps between two intensity and time charts (peak values) in the time domain.
   * The algorithm itself run O(n^2), but this is OK since the inputs are peak values, which on maximum are in the order
   * of 2 magnitudes (ie count < 100).
   * @param intensityAndTimeA - A list of pairs of double of intensity and time representing peak values.
   * @param intensityAndTimeB - A list of pairs of double of intensity and time representing peak values.
   * @param thresholdTime - This parameter is used to isolate by how much time difference between the peaks is deemed
   *                      OK for a positive detection.
   * @return True if there is an overlap in peaks between the two charts.
   */
  public static boolean doPeaksOverlap(List<Pair<Double, Double>> intensityAndTimeA,
                                       List<Pair<Double, Double>> intensityAndTimeB,
                                       Double thresholdTime) {
    for (Pair<Double, Double> point : intensityAndTimeB) {
      Double time = point.getRight();
      for (Pair<Double, Double> referencePoint : intensityAndTimeA) {
        Double referenceTime = referencePoint.getRight();
        if ((time > referenceTime - thresholdTime) && (time < referenceTime + thresholdTime)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * This function is a modification of this peak detection algorithm described here: http://www.billauer.co.il/peakdet.html.
   * Instead of using the first derivative's change in sign to detect a peak (which has a lot more false positives),
   * the algorithm detects peaks by making sure that on the adjacent side of a potential peak, there are valleys.
   * @param intensityAndTimeValues - A list of pairs of double of intensity and time.
   * @param threshold - This threshold is used to detect peaks and valleys.
   * @return - A list of pairs of intensity and time values corresponding to the peaks in the input values in ascending
   *           sorted order according to intensity.
   */
  public static List<Pair<Double, Double>> detectPeaksInIntensityTimeWaveform(
      ArrayList<Pair<Double, Double>> intensityAndTimeValues,
      Double threshold) {
    Double minIntensity = Double.MAX_VALUE;
    Double maxIntensity = -Double.MAX_VALUE;
    Double maxTime = 0.0;
    Double delta = threshold;
    ArrayList<Pair<Double, Double>> result = new ArrayList<>();

    boolean expectingPeak = true;

    for (Pair<Double, Double> val : intensityAndTimeValues) {
      Double intensity = val.getLeft();
      Double time = val.getRight();

      if (intensity > maxIntensity) {
        maxIntensity = intensity;
        maxTime = time;
      }

      if (intensity < minIntensity) {
        minIntensity = intensity;
      }

      if (expectingPeak) {

        // Since the current intensity has dropped by a reasonable amount, the last recorded maxIntensity
        // was a peak. So record that data.
        if (intensity < maxIntensity - delta) {
          result.add(new ImmutablePair<>(maxIntensity, maxTime));

          // The minIntensity is updated because all the previous minIntensity was on the left side of
          // the recently recorded peak, hence irrelevant. The current intensity is therefore the lowest
          // intensity value right after the peak.
          minIntensity = intensity;

          // Since we just added a new peak to the result set, a valley should follow. Therefore, do not
          // look for another peak now.
          expectingPeak = false;
        }
      } else {
        if (intensity > maxIntensity - delta) {
          // Reset maxIntensity and maxTime once valley has been found.
          maxIntensity = intensity;
          maxTime = time;

          // Since we just detected a valley, we now should expect a peak.
          expectingPeak = true;
        }
      }
    }

    // Sort in descending order of intensity
    Collections.sort(result, new Comparator<Pair<Double, Double>>() {
      @Override
      public int compare(Pair<Double, Double> o1, Pair<Double, Double> o2) {
        return o2.getLeft().compareTo(o1.getLeft());
      }
    });

    return result;
  }
}
