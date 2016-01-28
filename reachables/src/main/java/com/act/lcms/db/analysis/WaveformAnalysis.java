package com.act.lcms.db.analysis;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jcamp.math.Range;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WaveformAnalysis {

  //Delimiter used in CSV file
  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

  //CSV file header
  private static final String FILE_HEADER = "intensity,time";

  private static Pair<Double, Double> sumIntensityAndTimeList(List<Pair<Double, Double>> list) {
    Double time = list.get(0).getRight();
    Double intensitySum = 0.0;
    for (Pair<Double, Double> point : list) {
      intensitySum += point.getLeft();
    }
    return new ImmutablePair(intensitySum, time);
  }

  private static List<Pair<Double, Double>> standardDeviationOfIntensityTimeGraphs(List<List<Pair<Double, Double>>> graphs) {
    List<Pair<Double, Double>> stdList = new ArrayList<>(graphs.size());

    for (int i = 0; i < graphs.get(0).size(); i++) {
      Double representativeTime = graphs.get(0).get(i).getRight();
      Double intensitySum = 0.0;
      for (int j = 0; j < graphs.size(); j++) {
        List<Pair<Double, Double>> chart = graphs.get(j);
        intensitySum += chart.get(i).getLeft();
      }

      Double meanSquared = 0.0;
      Double mean = intensitySum/graphs.size();
      for (int j = 0; j < graphs.size(); j++) {
        List<Pair<Double, Double>> chart = graphs.get(j);
        meanSquared += Math.pow(chart.get(i).getLeft() - mean, 2);
      }

      Double standardDeviation = Math.pow(meanSquared/graphs.size(), 0.5);
      stdList.add(i, new ImmutablePair<>(standardDeviation, representativeTime));
    }

    return stdList;
  }

  public static List<Pair<Double, Double>> compressIntensityAndTimeGraphs(List<Pair<Double, Double>> intensityAndTime,
                                                                          int compressionMagnitude) {
    ArrayList<Pair<Double, Double>> compressedResult = new ArrayList<>();
    for (int i = 0; i < intensityAndTime.size()/compressionMagnitude; i++) {
      int startIndex = i * compressionMagnitude;
      int endIndex = startIndex + compressionMagnitude;
      compressedResult.add(sumIntensityAndTimeList(intensityAndTime.subList(startIndex,
          endIndex > intensityAndTime.size() ? intensityAndTime.size() : endIndex)));
    }

    return compressedResult;
  }

  public static Map<String, Pair<Double, Double>> performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNR(
      Map<String, Map<String, List<Pair<Double, Double>>>> ionToIntensityData, String standardChemical) {

    Map<String, Pair<Double, Double>> ionToSNR = new HashMap<>();
    for (String ion : ionToIntensityData.get(standardChemical).keySet()) {
      List<Pair<Double, Double>> standardIntensityTime =
          compressIntensityAndTimeGraphs(ionToIntensityData.get(standardChemical).get(ion), 5);
      List<List<Pair<Double, Double>>> negativeIntensityTimes = new ArrayList<>();

      for (String chemical : ionToIntensityData.keySet()) {
        if (!chemical.equals(standardChemical)) {
          negativeIntensityTimes.add(compressIntensityAndTimeGraphs(ionToIntensityData.get(chemical).get(ion), 5));
        }
      }

      List<Pair<Double, Double>> stds = standardDeviationOfIntensityTimeGraphs(negativeIntensityTimes);
      int totalCount = standardIntensityTime.size() > stds.size() ? stds.size() : standardIntensityTime.size();
      Double maxSNR = 0.0;
      Double maxTime = 0.0;
      for (int i = 0; i < totalCount; i++) {
        Double snr = standardIntensityTime.get(i).getLeft()/stds.get(i).getLeft();
        Double time  = standardIntensityTime.get(i).getRight();
        if (snr > maxSNR) {
          maxSNR = snr;
          maxTime = time;
        }
      }

      ionToSNR.put(ion, new ImmutablePair<>(maxSNR, maxTime));
    }

    // Convert Map to List
    List<Map.Entry<String, Pair<Double, Double>>> ionToHighestSNRList = new LinkedList<>(ionToSNR.entrySet());

    // Sort list with comparator in descending order of SNR, to compare the Map values
    Collections.sort(ionToHighestSNRList, new Comparator<Map.Entry<String, Pair<Double, Double>>>() {
      @Override
      public int compare(Map.Entry<String, Pair<Double, Double>> o1,
                         Map.Entry<String, Pair<Double, Double>> o2) {
        return (o2.getValue().getLeft()).compareTo(o1.getValue().getLeft());
      }
    });

    // sortedSNRMap stores the highest SNR of each ion corresponding to the positive
    // standard in descending order.
    Map<String, Pair<Double, Double>> sortedSNRMap = new LinkedHashMap<>();
    for (Map.Entry<String, Pair<Double, Double>> ionToIntensityTime : ionToHighestSNRList) {
      sortedSNRMap.put(ionToIntensityTime.getKey(), ionToIntensityTime.getValue());
    }

    return sortedSNRMap;
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

  public static void writeCsvFile(String fileName, ArrayList<Pair<Double, Double>> intensityAndTimeValues, boolean isStandard) {

    File dir;
    if (isStandard) {
      dir = new File("standard");
    } else {
      dir = new File("control");
    }

    FileWriter fileWriter = null;

    try {
      fileWriter = new FileWriter(new File(dir, fileName));

      //Write the CSV file header
      fileWriter.append(FILE_HEADER.toString());

      //Add a new line separator after the header
      fileWriter.append(NEW_LINE_SEPARATOR);

      for (Pair<Double, Double> val : intensityAndTimeValues) {
        Double intensity = val.getLeft();
        Double time = val.getRight();
        fileWriter.append(String.valueOf(intensity));
        fileWriter.append(COMMA_DELIMITER);
        fileWriter.append(String.valueOf(time));
        fileWriter.append(NEW_LINE_SEPARATOR);
      }
      System.out.println("CSV file was created successfully.");
    } catch (Exception e) {
      System.out.println("Error in CsvFileWriter.");
      e.printStackTrace();
    } finally {
      try {
        fileWriter.flush();
        fileWriter.close();
      } catch (IOException e) {
        System.out.println("Error while flushing/closing fileWriter.");
        e.printStackTrace();
      }
    }
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