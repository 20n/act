package com.act.biointerpretation.networkanalysis;

import com.act.jobs.FileChecker;
import com.act.lcms.v2.DetectedPeak;
import com.act.lcms.v2.FixedWindowDetectedPeak;
import com.act.lcms.v2.LcmsSpectrum;
import com.act.lcms.v2.PeakSpectrum;
import com.act.utils.TSVParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LcmsTSVParser {

  private static final String MZ_KEY = "mz";
  private static final String INT_KEY = "exp_maxo";
  private static final String RT_KEY = "rt";

  // 0.01 daltons is a good baseline tolerance for matching mz values between ions and peaks
  private static final Double MZ_TOLERANCE = .01;
  // This is currently irrelevant, but the peak requires some notion of an RT window, so we make one based on this.
  private static final Double RT_TOLERANCE = 1.0;

  private LcmsTSVParser() {
    // There's no reason to instantiate this class.
  }

  public static PeakSpectrum parseTSV(File lcmsTSVFile) throws IOException {
    FileChecker.verifyInputFile(lcmsTSVFile);
    TSVParser parser = new TSVParser();
    parser.parse(lcmsTSVFile);

    List<DetectedPeak> peaks = new ArrayList<>();

    for (Map<String, String> row : parser.getResults()) {
      Double mz = Double.parseDouble(row.get(MZ_KEY));
      Double intensity = row.get(INT_KEY).equals("") ? 0.0 : Double.parseDouble(row.get(INT_KEY));
      Double retentionTime = Double.parseDouble(row.get(RT_KEY));

      // We're abusing DetectedPeak's scan file field by pointing it to a TSV file instead of a scan file.
      // TODO: work out the proper way to do this.
      String scanFile = lcmsTSVFile.getAbsolutePath();

      if (intensity > 0) {
        FixedWindowDetectedPeak peak = new FixedWindowDetectedPeak(scanFile, mz, MZ_TOLERANCE,
            retentionTime, RT_TOLERANCE, intensity, 1.0);
        peaks.add(peak);
      }
    }

    return new LcmsSpectrum(peaks);
  }
}
