package com.act.lcms.db.analysis;

import act.shared.helpers.P;
import org.apache.commons.lang3.tuple.Pair;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class WaveformAnalysis {
  private static final int START_INDEX = 0;
  private static final int COMPRESSION_CONSTANT = 5;
  private static final Double DEFAULT_LOWEST_RMS_VALUE = 1.0;

  //Delimiter used in CSV file
  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

  /**
   * This function sums up over a series of intensity/time values.
   * @param list - A list of intensity/time points.
   * @return - A point of summed intensities with the time set to the start of the list.
   */
  private static Pair<Double, Double> sumIntensityAndTimeList(List<Pair<Double, Double>> list) {
    // We use the first value's time as a period over which the intensities are summed over. This is a conscious
    // choice to standardize the summation analysis. The trade offs are that the final output will
    // not have an accurate time period and will always underestimate the actual time, but since
    // the time period over which it is the summed is small (< 1 second), the underestimation is within comfortable
    // bounds.
    Double time = list.get(START_INDEX).getRight();
    Double intensitySum = 0.0;
    for (Pair<Double, Double> point : list) {
      intensitySum += point.getLeft();
    }
    return Pair.of(intensitySum, time);
  }

  /**
   * This function calculates the root mean squared of a collection of intensity/time graphs. It does this by finding
   * the root mean squared across every time period of the list of intensity/time graphs.
   * @param graphs - A list of intensity/time graphs
   * @return - A list of rms values.
   */
  private static List<Pair<Double, Double>> rmsOfIntensityTimeGraphs(List<List<Pair<Double, Double>>> graphs) {

    // Since the input graphs could be of different lengths, we need to find the smallest list as the representative
    // size to do the analysis or else we will get null pointer exceptions. Doing this is OK from an analysis perspective
    // since size differences only manifest at the end of the LCMS readings (ie. timings are the same at the start but
    // get truncated at the end) and there are <10 point difference between the graphs based on inspection.
    // TODO: Alert the user if these are huge size differences between the graphs.
    int representativeSize = graphs.get(START_INDEX).size();
    int representativeGraph = START_INDEX;

    for (int index = 0; index < graphs.size(); index++) {
      if (graphs.get(index).size() < representativeGraph) {
        representativeSize = graphs.get(index).size();
        representativeGraph = index;
      }
    }

    List<Pair<Double, Double>> rmsList = new ArrayList<>(representativeSize);
    for (int i = 0; i < representativeSize; i++) {

      // The representationTime is set to the time of the graph with the shortest length at the index i.
      Double representativeTime = graphs.get(representativeGraph).get(i).getRight();
      Double intensitySquaredSum = 0.0;

      // RMS is sqrt(sum(X^2)/len)
      for (int j = 0; j < graphs.size(); j++) {
        List<Pair<Double, Double>> chart = graphs.get(j);
        intensitySquaredSum += Math.pow(chart.get(i).getLeft(), 2);
      }

      Double rms = Math.pow(intensitySquaredSum / graphs.size(), 0.5);

      // We make sure the RMS is atleast one as our floor for the number of ions that hit the detector, so that we
      // do not get an amplification of the SNR based on extremely low noise conditions which exist in some of the
      // runs.
      if (rms < DEFAULT_LOWEST_RMS_VALUE) {
        rms = DEFAULT_LOWEST_RMS_VALUE;
      }

      rmsList.add(i, Pair.of(rms, representativeTime));
    }

    return rmsList;
  }

  /**
   * This function compresses a given list of time series data based on a period compression value.
   * @param intensityAndTime - A list of intensity/time data
   * @param compressionMagnitude - This value is the magnitude by which the data is compressed in the time dimension.
   * @return A list of intensity/time data is the compressed
   */
  public static List<Pair<Double, Double>> compressIntensityAndTimeGraphs(List<Pair<Double, Double>> intensityAndTime,
                                                                          int compressionMagnitude) {
    ArrayList<Pair<Double, Double>> compressedResult = new ArrayList<>();
    for (int i = 0; i < intensityAndTime.size() / compressionMagnitude; i++) {
      int startIndex = i * compressionMagnitude;
      int endIndex = startIndex + compressionMagnitude;
      List<Pair<Double, Double>> subListSum = intensityAndTime.subList(startIndex,
          endIndex > intensityAndTime.size() ? intensityAndTime.size() : endIndex);

      // Make sure that the size of the sublist has atleast one element in it.
      if (subListSum.size() > 0) {
        compressedResult.add(sumIntensityAndTimeList(subListSum));
      }
    }

    return compressedResult;
  }

  /**
   * This function takes in a standard molecules's intensity vs time data and a collection of negative controls data
   * and plots the SNR value at each time period, assuming the time jitter effects are negligible (more info on this
   * is here: https://github.com/20n/act/issues/136). Based on the snr values, it rank orders the metlin ions of the
   * molecule.
   * @param ionToIntensityData - A map of chemical to intensity/time data
   * @param standardChemical - The chemical that is the standard of analysis
   * @return - A sorted set of Metlin ion to (intensity, time) pairs
   */
  public static Set<Map.Entry<String, Pair<Double, Double>>> performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNR(
      ChemicalToMapOfMetlinIonsToIntensityTimeValues ionToIntensityData, String standardChemical) {

    Map<String, Pair<Double, Double>> ionToSNR = new HashMap<>();
    for (String ion : ionToIntensityData.getMetlinIonsOfChemical(standardChemical).keySet()) {
      List<Pair<Double, Double>> standardIntensityTime =
          compressIntensityAndTimeGraphs(ionToIntensityData.getMetlinIonsOfChemical(standardChemical).get(ion), COMPRESSION_CONSTANT);
      List<List<Pair<Double, Double>>> negativeIntensityTimes = new ArrayList<>();

      for (String chemical : ionToIntensityData.getIonList()) {
        if (!chemical.equals(standardChemical)) {
          negativeIntensityTimes.add(compressIntensityAndTimeGraphs(ionToIntensityData.getMetlinIonsOfChemical(chemical).get(ion), COMPRESSION_CONSTANT));
        }
      }

      List<Pair<Double, Double>> rmsOfNegativeValues = rmsOfIntensityTimeGraphs(negativeIntensityTimes);
      int totalCount = standardIntensityTime.size() > rmsOfNegativeValues.size() ? rmsOfNegativeValues.size() : standardIntensityTime.size();
      Double maxSNR = 0.0;
      Double maxTime = 0.0;
      for (int i = 0; i < totalCount; i++) {
        Double snr = Math.pow(standardIntensityTime.get(i).getLeft() / rmsOfNegativeValues.get(i).getLeft(), 2);
        Double time = standardIntensityTime.get(i).getRight();
        if (snr > maxSNR) {
          maxSNR = snr;
          maxTime = time;
        }
      }

      ionToSNR.put(ion, Pair.of(maxSNR, maxTime));
    }

    // sortedSNRMap stores the highest SNR of each ion corresponding to the positive
    // standard in descending order.
    SortedSet<Map.Entry<String, Pair<Double, Double>>> sortedSNRSet =
        new TreeSet<Map.Entry<String, Pair<Double, Double>>>(new Comparator<Map.Entry<String, Pair<Double, Double>>>() {
          @Override
          public int compare(Map.Entry<String, Pair<Double, Double>> o1,
                             Map.Entry<String, Pair<Double, Double>> o2) {
            return (o2.getValue().getLeft()).compareTo(o1.getValue().getLeft());
          }
      });

    sortedSNRSet.addAll(ionToSNR.entrySet());
    return sortedSNRSet;
  }

  public static void printIntensityTimeGraphInCSVFormat(List<Pair<Double, Double>> values, String fileName) throws Exception {
    FileWriter chartWriter = new FileWriter(fileName);
    chartWriter.append("Intensity, Time");
    chartWriter.append(NEW_LINE_SEPARATOR);
    for (Pair<Double, Double> point : values) {
      chartWriter.append(point.getLeft().toString());
      chartWriter.append(COMMA_DELIMITER);
      chartWriter.append(point.getRight().toString());
      chartWriter.append(NEW_LINE_SEPARATOR);
    }
    chartWriter.flush();
    chartWriter.close();
  }

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
          result.add(Pair.of(maxIntensity, maxTime));

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
