package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang3.tuple.Pair;
import com.act.lcms.LCMSXMLParser.LCMSSpectrum;

public class MzMLSmoothing {

  private List<Pair<Double, Double>> windowAverage(List<Pair<Double, Double>> raw, int wBy2) {
    List<Pair<Double, Double>> windowed = new ArrayList<>();

    // add the first half window as is
    for (int i=0; i<wBy2; i++) {
      windowed.add(raw.get(i));
    }

    // init a running average, with: intensity(idx in [0, 2w)) + intensity(idx = 0) - intensity(idx = 2w)
    // the last two elements are because the next loop will immediately subtract idx=0, and add idx=2w
    Double runIntAvg = 0.0;
    int winSz = wBy2 * 2;
    for (int i=0; i<winSz; i++)
      runIntAvg += raw.get(i).getRight();
    runIntAvg += raw.get(0).getRight(); // double add, we know. see comment above
    runIntAvg -= raw.get(winSz).getRight(); // removes last, we know. see comment above
    runIntAvg /= winSz;

    for (int i=wBy2; i<raw.size()-wBy2; i++) {
      Pair<Double, Double> rmFromWindow = raw.get(i - wBy2);
      Pair<Double, Double> addToWindow = raw.get(i + wBy2);
      // update the running average, remove the contribution of the element that left the window (val/winSz)
      // and add the contribution of the element that moved into the window (val/winSz)
      runIntAvg -= (rmFromWindow.getRight() / winSz); 
      runIntAvg += (addToWindow.getRight() / winSz); 
      // create a new mz, intensity pair
      Pair<Double, Double> newMzInt = Pair.of(raw.get(i).getLeft(), runIntAvg);
      // install that intensity pair into the timepoint array
      windowed.add(newMzInt);
    }

    // add the last half window as is
    for (int i=raw.size()-wBy2; i<raw.size(); i++) {
      windowed.add(raw.get(i));
    }

    return windowed;
  }

  private List<Pair<Double, Double>> smooth(List<Pair<Double, Double>> raw) {
    return windowAverage(raw, 5);
  }

  private Pair<Double, Double> findBasePeak(List<Pair<Double, Double>> raw) {
    Pair<Double, Double> max = null;
    for (Pair<Double, Double> mz_int : raw) {
      if (max == null || max.getRight() < mz_int.getRight())
        max = mz_int;
    }
    return max;
  }

  private LCMSSpectrum smooth(LCMSSpectrum raw) {
    List<Pair<Double, Double>> intensities = smooth(raw.getIntensities());
    Pair<Double, Double> maxPeak = findBasePeak(intensities);
    Double basePeakMZ = maxPeak.getLeft(), basePeakIntensity = maxPeak.getRight();

    LCMSSpectrum smoothedTimeSpecta = new LCMSSpectrum(raw.getIndex(), raw.getTimeVal(), raw.getTimeUnit(), intensities,
        basePeakMZ, basePeakIntensity, raw.getFunction(), raw.getScan());
    return smoothedTimeSpecta;
  }

  public void validateUsingInstrumentsBasePeaks(String fileName, int howManyToValidate) throws Exception {
    List<LCMSSpectrum> spectrumObjs = null;

    LCMSXMLParser parser = new LCMSXMLParser();
    if (fileName.endsWith(".mzML")) {
      spectrumObjs = parser.parse(fileName);
    } else {
      String msg = "Need a .mzML file or serialized data:\n" +
        "   - .mzML file (use msconvert/Proteowizard for Waters RAW->mzML)\n" + 
        "   - .LCMSSpectrum.serialized file (LCMSXMLParser serialization)";
      throw new RuntimeException(msg);
    }

    // validate objects
    howManyToValidate = howManyToValidate != -1 ? howManyToValidate : spectrumObjs.size();
    Double mzErr = 0.0, mzE = 0.0, itErr = 0.0, itE = 0.0;
    for (int i=0; i<howManyToValidate; i++) {
      LCMSSpectrum raw = spectrumObjs.get(i);
      LCMSSpectrum smoothed = smooth(raw);
      mzErr += (mzE = normalizedPcError(smoothed.getBasePeakMZ(), raw.getBasePeakMZ()));
      itErr += (itE = normalizedPcError(smoothed.getBasePeakIntensity(), raw.getBasePeakIntensity()));
      System.out.format("Timepoint = %.4f. %% error: mz = %.2f, intensity = %.2f\n", raw.getTimeVal(), mzE, itE);
    }
    // average out the mz and intensity errors
    mzErr /= howManyToValidate;
    itErr /= howManyToValidate;

    // convert them to percentage values
    mzErr *= 100;
    itErr *= 100;

    // report to user
    System.out.format("%d Timepoints processed. Aggregate %% error: mz = %.2f, intensity = %.2f\n", 
        howManyToValidate, mzErr, itErr);
  }

  private Double normalizedPcError(Double val, Double baseline) {
    // computes the absolute val error of `val` against the `baseline` as a % (btwn 0,1) of baseline)
    Double error = Math.abs(val - baseline);
    return error/Math.abs(baseline);
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new RuntimeException("Needs (1) .mzML or serialized file, (2) how many (-1 for all)");
    }

    new MzMLSmoothing().validateUsingInstrumentsBasePeaks(args[0], Integer.parseInt(args[1]));
  }
}
