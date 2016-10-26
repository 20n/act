package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.lcms.v2.DetectedPeak;
import com.act.lcms.v2.FixedWindowDetectedPeak;
import com.act.lcms.v2.PeakSpectrum;
import com.act.utils.TSVParser;
import com.jacob.com.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by gil on 10/26/16.
 */
public class LcmsTSVParser {

  public static PeakSpectrum parseTSV(File lcmsTSVFile) throws IOException {
    FileChecker.verifyInputFile(lcmsTSVFile);
    TSVParser parser = new TSVParser();
    parser.parse(lcmsTSVFile);

    TSVSpectrum spectrum = new TSVSpectrum();

    for (Map<String, String> row : parser.getResults()) {
      Double minMz = Double.parseDouble(row.get("mzmin"));
      Double maxMz = Double.parseDouble(row.get("mzmax"));
      Double intensity = Double.parseDouble(row.get("exp_maxo"));
      Double retentionTime = Double.parseDouble(row.get("rt"));
      Double retentionWindow = (Double.parseDouble(row.get("rtmax")) - Double.parseDouble(row.get("rtmin"))) / 2;
      String scanFile = lcmsTSVFile.getAbsolutePath();

      if (intensity > 0) {
        FixedWindowDetectedPeak peak = new FixedWindowDetectedPeak(scanFile, (minMz + maxMz) / 2, (maxMz - minMz) / 2,
            retentionTime, retentionWindow, intensity, 1.0);
        spectrum.addPeak(peak);
      }
    }

    return spectrum;
  }

  private static class TSVSpectrum implements PeakSpectrum {

    List<DetectedPeak> peaks;

    public TSVSpectrum() {
      peaks = new ArrayList<>();
    }

    public void addPeak(DetectedPeak peak) {
      peaks.add(peak);
    }

    @Override
    public List<DetectedPeak> getAllPeaks() {
      return peaks;
    }

    @Override
    public List<DetectedPeak> getPeaks(Predicate<DetectedPeak> filter) {
      return peaks.stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public List<DetectedPeak> getPeaksByMass(Double mass, Double massTolerance) {
      return getPeaks(p -> Math.abs(p.getMz() - mass) < massTolerance);
    }

    @Override
    public List<DetectedPeak> getPeaksByTime(Double time, Double timeTolerance) {
      throw new NotImplementedException("Not implemented.");
    }

    @Override
    public List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double massTolerance, Double timeTolerance) {
      throw new NotImplementedException("Not implemented.");
    }

    @Override
    public List<DetectedPeak> getNeighborhoodPeaks(Double mass, Double massTolerance, Double time, Double timeTolerance) {
      throw new NotImplementedException("Not implemented.");
    }

    @Override
    public Map<String, PeakSpectrum> getPeakSpectraByScanFile() {
      throw new NotImplementedException("Not implemented.");
    }

    @Override
    public PeakSpectrum getSpectrum(String scanFileId) {
      throw new NotImplementedException("Not implemented.");
    }
  }
}
