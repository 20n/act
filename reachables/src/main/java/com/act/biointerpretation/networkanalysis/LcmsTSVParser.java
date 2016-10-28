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

public class LcmsTSVParser {

  private static final String MZ_KEY = "mz";
  private static final String INT_KEY = "exp_maxo";
  private static final String RT_KEY = "rt";

  // 0.01 daltons is a good baseline tolerance for matching mz values between ions and peaks
  private static final Double MZ_TOLERANCE = .01;
  // This is currently irrelevant, but the peak requires some notion of an RT window, so we make one based on this.
  private static final Double RT_TOLERANCE = 1.0;

  public static PeakSpectrum parseTSV(File lcmsTSVFile) throws IOException {
    FileChecker.verifyInputFile(lcmsTSVFile);
    TSVParser parser = new TSVParser();
    parser.parse(lcmsTSVFile);

    TSVSpectrum spectrum = new TSVSpectrum();

    int i = 0;
    for (Map<String, String> row : parser.getResults()) {
      Double mz = getDouble(row.get(MZ_KEY));
      Double intensity = getDouble(row.get(INT_KEY));
      Double retentionTime = getDouble(row.get(RT_KEY));
      String scanFile = lcmsTSVFile.getAbsolutePath();

      if (intensity > 0) {
        FixedWindowDetectedPeak peak = new FixedWindowDetectedPeak(scanFile, mz - MZ_TOLERANCE, mz + MZ_TOLERANCE,
            retentionTime, RT_TOLERANCE, intensity, 1.0);
        spectrum.addPeak(peak);
      }
    }

    return spectrum;
  }

  private static Double getDouble(String raw) {
    return (raw.equals("")) ? 0.0 : Double.parseDouble(raw);
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
