package com.act.lcms.db.analysis;

import com.act.lcms.XZ;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class WaveformAnalysis {
  private static final int START_INDEX = 0;
  private static final int COMPRESSION_CONSTANT = 5;
  private static final Double DEFAULT_LOWEST_RMS_VALUE = 1.0;

  // We chose this value as a heuristic on how much time drift we are willing to accept for our analysis in seconds.
  private static final Double RESTRICTED_RETENTION_TIME_WINDOW_IN_SECONDS = 5.0;

  // This constant is applied to comparing the negative controls against the positive.
  private static final Double POSITION_TIME_WINDOW_IN_SECONDS = 1.0;

  //Delimiter used in CSV file
  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

  /**
   * This function sums up over a series of intensity/time values.
   * @param list A list of intensity/time points.
   * @return A point of summed intensities with the time set to the start of the list.
   */
  private static XZ sumIntensityAndTimeList(List<XZ> list) {
    // We use the first value's time as a period over which the intensities are summed over. This is a conscious
    // choice to standardize the summation analysis. The trade offs are that the final output will
    // not have an accurate time period and will always underestimate the actual time, but since
    // the time period over which it is the summed is small (< 1 second), the underestimation is within comfortable
    // bounds.
    Double time = list.get(START_INDEX).getTime();
    Double intensitySum = 0.0;
    for (XZ point : list) {
      intensitySum += point.getIntensity();
    }

    return new XZ(time, intensitySum);
  }

  /**
   * This function calculates the root mean squared of a collection of intensity/time graphs. It does this by finding
   * the root mean squared across every time period of the list of intensity/time graphs.
   * @param graphs A list of intensity/time graphs
   * @return A list of rms values.
   */
  private static List<XZ> rmsOfIntensityTimeGraphs(List<List<XZ>> graphs) {
    // Since the input graphs could be of different lengths, we need to find the smallest list as the representative
    // size to do the analysis or else we will get null pointer exceptions. Doing this is OK from an analysis perspective
    // since size differences only manifest at the end of the LCMS readings (ie. timings are the same at the start but
    // get truncated at the end) and there are <10 point difference between the graphs based on inspection.
    // TODO: Alert the user if there are huge size differences between the graph size.
    int representativeSize = graphs.get(START_INDEX).size();
    for (List<XZ> graph : graphs) {
      representativeSize = Math.min(representativeSize, graph.size());
    }

    int representativeGraph = START_INDEX;

    for (int index = 0; index < graphs.size(); index++) {
      if (graphs.get(index).size() < representativeGraph) {
        representativeSize = graphs.get(index).size();
        representativeGraph = index;
      }
    }

    List<XZ> rmsList = new ArrayList<>(representativeSize);
    for (int i = 0; i < representativeSize; i++) {

      // The representationTime is set to the time of the graph with the shortest length at the index i.
      Double representativeTime = graphs.get(representativeGraph).get(i).getTime();
      Double intensitySquaredSum = 0.0;

      // RMS is sqrt(sum(X^2)/len)
      for (int j = 0; j < graphs.size(); j++) {
        List<XZ> chart = graphs.get(j);
        intensitySquaredSum += Math.pow(chart.get(i).getIntensity(), 2);
      }

      Double rms = Math.pow(intensitySquaredSum / graphs.size(), 0.5);

      // We make sure the RMS is atleast one as our floor for the number of ions that hit the detector, so that we
      // do not get an amplification of the SNR based on extremely low noise conditions which exist in some of the
      // runs.
      if (rms < DEFAULT_LOWEST_RMS_VALUE) {
        rms = DEFAULT_LOWEST_RMS_VALUE;
      }

      rmsList.add(i, new XZ(representativeTime, rms));
    }

    return rmsList;
  }

  /**
   * The noise of a given spectrum using a biased std derivation
   * @param spectrum
   * @return The relative noise of a spectrum.
   */
  public static Double noiseOfSpectrum(List<XZ> spectrum) {
    Double movingAverage = 0.0;
    Double averageMeanSquared = 0.0;
    Double sizeOfSpectrum = spectrum.size() * 1.0;

    for (XZ point : spectrum) {
      movingAverage += point.getIntensity() / sizeOfSpectrum;
    }

    for (XZ point : spectrum) {
      averageMeanSquared += Math.pow(point.getIntensity() - movingAverage, 2) / sizeOfSpectrum;
    }

    return Math.pow(averageMeanSquared, 0.5);
  }

  /**
   * This function returns the maximum noise among a map of ion to list of spectra
   * @param spectra A map of ion to spectrum
   * @return The maximum noise of the map
   */
  public static Double maxNoiseOfSpectra(Map<String, List<XZ>> spectra) {
    Double maxNoise = Double.MIN_VALUE;
    for (Map.Entry<String, List<XZ>> ionToSpectrum : spectra.entrySet()) {
      maxNoise = Math.max(maxNoise, noiseOfSpectrum(ionToSpectrum.getValue()));
    }
    return maxNoise;
  }

  /**
   * This function compresses a given list of time series data based on a period compression value.
   * @param intensityAndTime A list of intensity/time data
   * @param compressionMagnitude This value is the magnitude by which the data is compressed in the time dimension.
   * @return A list of intensity/time data is the compressed
   */
  public static Pair<List<XZ>, Map<Double, Double>> compressIntensityAndTimeGraphs(List<XZ> intensityAndTime, int compressionMagnitude) {
    ArrayList<XZ> compressedResult = new ArrayList<>();
    Map<Double, Double> timeToIntensity = new HashMap<>();

    if (intensityAndTime == null) {
      System.out.println("intensity time is null");
      System.exit(1);
    }

    for (int i = 0; i < intensityAndTime.size() / compressionMagnitude; i++) {
      int startIndex = i * compressionMagnitude;
      int endIndex = startIndex + compressionMagnitude;
      List<XZ> subListSum = intensityAndTime.subList(startIndex,
          endIndex > intensityAndTime.size() ? intensityAndTime.size() : endIndex);

      Double maxIntensity = 0.0;
      for (XZ xz : subListSum) {
        maxIntensity = Math.max(maxIntensity, xz.getIntensity());
      }

      // Make sure that the size of the sublist has atleast one element in it.
      if (subListSum.size() > 0) {
        compressedResult.add(sumIntensityAndTimeList(subListSum));
        timeToIntensity.put(subListSum.get(START_INDEX).getTime(), maxIntensity);
      }
    }

    return Pair.of(compressedResult, timeToIntensity);
  }

  public static Map<String, Pair<XZ, Double>> performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForNormalWells(
      ChemicalToMapOfMetlinIonsToIntensityTimeValues ionToIntensityDataPos,
      List<ChemicalToMapOfMetlinIonsToIntensityTimeValues> ionToIntensityDataNegList,
      List<Pair<String, Double>> searchMZs) {

    Map<String, Pair<XZ, Double>> result = new HashMap<>();

    for (Pair<String, Double> mz : searchMZs) {
      Pair<List<XZ>, Map<Double, Double>> positiveXZValuesAndMaxIntensity = compressIntensityAndTimeGraphs(
          ionToIntensityDataPos.getMetlinIonsOfChemical(
              AnalysisHelper.getChemicalNameFromWellInformation(
                  mz.getLeft(), ScanData.KIND.POS_SAMPLE)).get(mz.getLeft()), COMPRESSION_CONSTANT);

      List<XZ> positiveIntensityTime =
          detectPeaksInIntensityTimeWaveform(positiveXZValuesAndMaxIntensity.getLeft(), PEAK_DETECTION_THRESHOLD);

      List<List<XZ>> negativeIntensityTimes = new ArrayList<>();
      for (ChemicalToMapOfMetlinIonsToIntensityTimeValues neg : ionToIntensityDataNegList) {
        negativeIntensityTimes.add(
            compressIntensityAndTimeGraphs(
                neg.getMetlinIonsOfChemical(
                    AnalysisHelper.getChemicalNameFromWellInformation(
                        mz.getLeft(), ScanData.KIND.NEG_CONTROL)).get(mz.getLeft()), COMPRESSION_CONSTANT).getLeft());
      }

      List<XZ> rmsOfNegativeValues = rmsOfIntensityTimeGraphs(negativeIntensityTimes);

      Double maxSNR = 0.0;
      Double maxTime = 0.0;
      Double peakIntensity = 0.0;

      // For each of the peaks detected in the positive control, find the spectral intensity values from the negative
      // controls and calculate SNR based on that.
      for (XZ positivePosition : positiveIntensityTime) {

        Double time = positivePosition.getTime();

        XZ negativeControlPosition = null;
        for (XZ position : rmsOfNegativeValues) {
          if (position.getTime() > time - POSITION_TIME_WINDOW_IN_SECONDS &&
              position.getTime() < time + POSITION_TIME_WINDOW_IN_SECONDS) {
            negativeControlPosition = position;
            break;
          }
        }

        Double snr = negativeControlPosition == null ? 0 :
            Math.pow(positivePosition.getIntensity() / negativeControlPosition.getIntensity(), 2);

        if (snr > maxSNR) {
          maxSNR = snr;
          maxTime = time;
          peakIntensity = positiveXZValuesAndMaxIntensity.getRight().get(positivePosition.getTime());
        }
      }

      result.put(mz.getLeft(), Pair.of(new XZ(maxTime, maxSNR), peakIntensity));
    }

    return result;
  }

  /**
   * This function takes in a standard molecules's intensity vs time data and a collection of negative controls data
   * and plots the SNR value at each time period, assuming the time jitter effects are negligible (more info on this
   * is here: https://github.com/20n/act/issues/136). Based on the snr values, it rank orders the metlin ions of the
   * molecule.
   * @param ionToIntensityData A map of chemical to intensity/time data
   * @param standardChemical The chemical that is the standard of analysis
   * @return A sorted linked hash map of Metlin ion to (intensity, time) pairs from highest intensity to lowest
   */
  public static LinkedHashMap<String, XZ> performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNR(
      ChemicalToMapOfMetlinIonsToIntensityTimeValues ionToIntensityData, String standardChemical,
      Map<String, List<Double>> restrictedTimeWindows) {

    TreeMap<Double, List<String>> sortedIntensityToIon = new TreeMap<>(Collections.reverseOrder());
    Map<String, XZ> ionToSNR = new HashMap<>();

    for (String ion : ionToIntensityData.getMetlinIonsOfChemical(standardChemical).keySet()) {

      // We first compress the ion spectra by 5 seconds (this number was gotten from trial and error on labelled
      // spectra). Then, we do feature detection of peaks in the compressed data.
      List<XZ> standardIntensityTime = detectPeaksInIntensityTimeWaveform(compressIntensityAndTimeGraphs(
          ionToIntensityData.getMetlinIonsOfChemical(standardChemical).get(ion), COMPRESSION_CONSTANT).getLeft(), PEAK_DETECTION_THRESHOLD);

      List<List<XZ>> negativeIntensityTimes = new ArrayList<>();
      for (String chemical : ionToIntensityData.getIonList()) {
        if (!chemical.equals(standardChemical)) {
          negativeIntensityTimes.add(compressIntensityAndTimeGraphs(ionToIntensityData.getMetlinIonsOfChemical(chemical).get(ion), COMPRESSION_CONSTANT).getLeft());
        }
      }

      List<XZ> rmsOfNegativeValues = rmsOfIntensityTimeGraphs(negativeIntensityTimes);

      List<Double> listOfTimeWindows = new ArrayList<>();
      if (restrictedTimeWindows != null && restrictedTimeWindows.get(ion) != null) {
        listOfTimeWindows.addAll(restrictedTimeWindows.get(ion));
      }

      Boolean canUpdateMaxSNRAndTime = true;
      Boolean useRestrictedTimeWindowAnalysis = false;

      // If there are restricted time windows, set the default to not update SNR until certain conditions are met.
      if (listOfTimeWindows.size() > 0) {
        useRestrictedTimeWindowAnalysis = true;
        canUpdateMaxSNRAndTime = false;
      }

      Double maxSNR = 0.0;
      Double maxTime = 0.0;

      // For each of the peaks detected in the positive control, find the spectral intensity values from the negative
      // controls and calculate SNR based on that.
      for (XZ positivePosition : standardIntensityTime) {

        Double time = positivePosition.getTime();

        XZ negativeControlPosition = null;
        for (XZ position : rmsOfNegativeValues) {
          if (position.getTime() > time - POSITION_TIME_WINDOW_IN_SECONDS &&
              position.getTime() < time + POSITION_TIME_WINDOW_IN_SECONDS) {
            negativeControlPosition = position;
            break;
          }
        }

        Double snr = Math.pow(positivePosition.getIntensity() / negativeControlPosition.getIntensity(), 2);

        // If the given time point overlaps with one of the restricted time windows, we can update the snr calculations.
        for (Double restrictedTimeWindow : listOfTimeWindows) {
          if ((time > restrictedTimeWindow - RESTRICTED_RETENTION_TIME_WINDOW_IN_SECONDS) &&
              (time < restrictedTimeWindow + RESTRICTED_RETENTION_TIME_WINDOW_IN_SECONDS)) {
            canUpdateMaxSNRAndTime = true;
            break;
          }
        }

        if (canUpdateMaxSNRAndTime) {
          maxSNR = Math.max(maxSNR, snr);
          maxTime = Math.max(maxTime, time);
        }

        if (useRestrictedTimeWindowAnalysis) {
          canUpdateMaxSNRAndTime = false;
        }
      }

      ionToSNR.put(ion, new XZ(maxTime, maxSNR));

      List<String> ionValues = sortedIntensityToIon.get(maxSNR);
      if (ionValues == null) {
        ionValues = new ArrayList<>();
        sortedIntensityToIon.put(maxSNR, ionValues);
      }

      ionValues.add(ion);
    }

    LinkedHashMap<String, XZ> result = new LinkedHashMap<>(sortedIntensityToIon.size());
    for (Map.Entry<Double, List<String>> entry : sortedIntensityToIon.entrySet()) {
      List<String> ions = entry.getValue();
      for (String ion : ions) {
        result.put(ion, ionToSNR.get(ion));
      }
    }

    return result;
  }

  public static void printIntensityTimeGraphInCSVFormat(List<XZ> values, String fileName) throws Exception {
    FileWriter chartWriter = new FileWriter(fileName);
    chartWriter.append("Intensity, Time");
    chartWriter.append(NEW_LINE_SEPARATOR);
    for (XZ point : values) {
      chartWriter.append(point.getIntensity().toString());
      chartWriter.append(COMMA_DELIMITER);
      chartWriter.append(point.getTime().toString());
      chartWriter.append(NEW_LINE_SEPARATOR);
    }
    chartWriter.flush();
    chartWriter.close();
  }

  /**
   * This function checks if there are overlaps between two intensity and time charts (peak values) in the time domain.
   * The algorithm itself run O(n^2), but this is OK since the inputs are peak values, which on maximum are in the order
   * of 2 magnitudes (ie count < 100).
   * @param intensityAndTimeA A list of XZ values.
   * @param intensityAndTimeB A list of XZ values.
   * @param thresholdTime This parameter is used to isolate by how much time difference between the peaks is deemed
   *                      OK for a positive detection.
   * @return True if there is an overlap in peaks between the two charts.
   */
  public static boolean doPeaksOverlap(List<XZ> intensityAndTimeA,
                                       List<XZ> intensityAndTimeB,
                                       Double thresholdTime) {
    for (XZ point : intensityAndTimeB) {
      Double time = point.getTime();
      for (XZ referencePoint : intensityAndTimeA) {
        Double referenceTime = referencePoint.getTime();
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
   * @param intensityAndTimeValues A list of pairs of double of intensity and time.
   * @param threshold This threshold is used to detect peaks and valleys.
   * @return A sorted list of XZ values corresponding to the peaks in the input values in ascending
   *           sorted order according to intensity.
   */
  public static List<XZ> detectPeaksInIntensityTimeWaveform(
      List<XZ> intensityAndTimeValues,
      Double threshold) {
    Double minIntensity = Double.MAX_VALUE;
    Double maxIntensity = -Double.MAX_VALUE;
    Double maxTime = 0.0;
    Double delta = threshold;
    ArrayList<XZ> result = new ArrayList<>();

    boolean expectingPeak = true;

    for (XZ val : intensityAndTimeValues) {
      Double intensity = val.getIntensity();
      Double time = val.getTime();

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
          result.add(new XZ(maxTime, maxIntensity));

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
    Collections.sort(result, new Comparator<XZ>() {
      @Override
      public int compare(XZ o1, XZ o2) {
        return o2.getIntensity().compareTo(o1.getIntensity());
      }
    });

    return result;
  }

  // The peak detection value was selected after testing it among various intensity time values and choosing
  // a constant that did not let too many false positive peaks through but was selective enough to detect a
  // reasonable number of peaks for downstream processing.
  private static final Double PEAK_DETECTION_THRESHOLD = 250.0d;

  // We chose the 3 best peaks since after 3, since we almost never check for comparisons between the 4th best peak
  // in the standards chromatogram vs other results.
  private static final Integer NUMBER_OF_BEST_PEAKS_TO_SELECTED_FROM = 3;

  // This value indicates to how much error we can tolerate between peak intensity times (in seconds).
  private static final Integer TIME_SKEW_CORRECTION = 1;

  /**
   * This function picks the best retention time among the best peaks from the standard wells. The algorithm is
   * looking for the following heuristics for standard well peak detection: a) a great peak profile
   * b) magnitude of peak is high c) the well is not from MeOH media. It implements this by picking the global
   * 3 best peaks from ALL the standard wells which are not in MeOH media using a peak feature detector. It then
   * compares overlaps between these peaks against the local 3 best peaks of the negative controls and positive samples.
   * If there is an overlap, we have detected a positive signal.
   * @param standardWells The list of standard wells to benchmark from
   * @param representativeMetlinIon This is the metlin ion that is used for the analysis, usually it is the best
   *                                metlin ion picked up an algorithm among the standard well scans.
   * @param positiveAndNegativeWells These are positive and negative wells against which the retention times are
   *                                 compared to see for overlaps.
   * @return A map of Scandata to XZ values for those signals where peaks match between the standard and pos/neg runs.
   */
  public static Map<ScanData<LCMSWell>, XZ> pickBestRepresentativeRetentionTimeFromStandardWells(
      List<ScanData<StandardWell>> standardWells, String representativeMetlinIon,
      List<ScanData<LCMSWell>> positiveAndNegativeWells) {

    List<XZ> bestStandardPeaks = new ArrayList<>();
    for (ScanData<StandardWell> well : standardWells) {
      if (well.getWell() != null) {
        // For retention times, select standard runs where the media is not MeOH since
        // MeOH has a lot more skew in retention time than other media. Moreover, none
        // of the feeding runs have their media as MeOH.
        if (well.getWell().getMedia() == null || !well.getWell().getMedia().equals("MeOH")) {
          bestStandardPeaks.addAll(detectPeaksInIntensityTimeWaveform(
              well.getMs1ScanResults().getIonsToSpectra().get(representativeMetlinIon), PEAK_DETECTION_THRESHOLD));
        }
      }
    }

    // Sort in descending order of intensity
    Collections.sort(bestStandardPeaks, new Comparator<XZ>() {
      @Override
      public int compare(XZ o1, XZ o2) {
        return o2.getIntensity().compareTo(o1.getIntensity());
      }
    });

    Map<ScanData<LCMSWell>, XZ> result = new HashMap<>();

    // Select from the top peaks in the standards run
    for (ScanData<LCMSWell> well : positiveAndNegativeWells) {
      List<XZ> topPeaksOfSample = detectPeaksInIntensityTimeWaveform(
              well.getMs1ScanResults().getIonsToSpectra().get(representativeMetlinIon), PEAK_DETECTION_THRESHOLD);

      for (XZ topPeak : bestStandardPeaks.subList(0, NUMBER_OF_BEST_PEAKS_TO_SELECTED_FROM - 1)) {
        int count =
            topPeaksOfSample.size() >= NUMBER_OF_BEST_PEAKS_TO_SELECTED_FROM ? NUMBER_OF_BEST_PEAKS_TO_SELECTED_FROM - 1
                : topPeaksOfSample.size();

        // Collisions do not matter here since we are just going to pick the highest intensity peak match, so ties
        // are arbitarily broker based on the order for access in the for loop below.
        TreeMap<Double, XZ> intensityToIntensityTimeValue = new TreeMap<>(Collections.reverseOrder());

        for (int i = 0; i < count; i++) {
          if (topPeaksOfSample.get(i).getTime() > topPeak.getTime() - TIME_SKEW_CORRECTION &&
              topPeaksOfSample.get(i).getTime() < topPeak.getTime() + TIME_SKEW_CORRECTION) {
            // There has been significant overlap in peaks between standard and sample.
            intensityToIntensityTimeValue.put(topPeaksOfSample.get(i).getIntensity(),
                topPeaksOfSample.get(i));
          }
        }

        if (intensityToIntensityTimeValue.keySet().size() > 0) {
          // Get the best peak overlap based on the largest magnitude intensity
          result.put(well, intensityToIntensityTimeValue.firstEntry().getValue());
        }
      }
    }

    return result;
  }
}
