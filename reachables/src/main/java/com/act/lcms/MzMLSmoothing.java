package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.lang3.tuple.Pair;
import com.act.lcms.LCMSXMLParser.LCMSSpectrum;

import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.UnivariateFunction;

public class MzMLSmoothing {

  private List<Pair<Double, Double>> spline(List<Pair<Double, Double>> raw) {
    double[] x = new double[raw.size()];
    double[] y = new double[raw.size()];
    for (int i=0; i<raw.size(); i++) {
      Pair<Double, Double> mz_int = raw.get(i);
      x[i] = mz_int.getLeft();
      y[i] = mz_int.getRight();
    }

    SplineInterpolator si = new SplineInterpolator();
    PolynomialSplineFunction f = si.interpolate(x, y);

    // we use a BrentSolver to find the zeros of the derivative
    UnivariateFunction d = f.derivative();

    // BrentSolver solves to a certain accuracy:
    // assuming the accuracy is on the m/z, lets go with 0.01 Da
    double absAccuracy = 0.0001; 
    BrentSolver solver = new BrentSolver(absAccuracy);
    
    int overwritten = 0;
    List<Pair<Double, Double>> interpolated = new ArrayList<>();
    // add the first element as is
    interpolated.add(raw.get(0));
    // iterate through [1,sz-1) and see if the peak needs to be shifted
    // it will need shifting if there is a maxima around this mz value
    for (int i=1; i<raw.size()-1; i++) {
      double mzBefore = raw.get(i-1).getLeft();
      double mzAfter = raw.get(i+1).getLeft(); 
      double mz = raw.get(i).getLeft();
      double it = f.value(mz); // spline value

      // check if there was a maxima in between these two nodes
      // if yes, we need to see where the spline root is in between and 
      // rewrite the entry for this mz
      if (Math.signum(d.value(mzBefore)) == 1 && Math.signum(d.value(mzAfter)) == -1) {
        // check if there is a zero close by, if yes put that as the mz value
        // else put whatever we already see
        int maxEval = 10000;
        // have to provide it a range within [lowerBound, upperBound]
        // hence the bracketing for the left and right extremes of mzs
        double min = mzBefore;
        double max = mzAfter;
        double root = solver.solve(maxEval, d, min, max);
        // System.out.format("Brent: mz: %.4f it: %.0f brent solved mz = %.4f val = %.0f\n", mz, it, root, f.value(root));
        // set the mz to this new value, and intensity to spline.value(mz)
        mz = root;
        it = f.value(root);
        overwritten++;
      }

      interpolated.add(Pair.of(mz, it));
    }
    // add the last element as is
    interpolated.add(raw.get(raw.size()-1));
    // System.out.format("Of total %d peaks, %d shifted right to the maximas.", raw.size(), overwritten);
    return interpolated;
  }

  private List<Pair<Double, Double>> smooth(List<Pair<Double, Double>> raw) {
    ensureSortedOnMz(raw);
    return spline(raw);
  }

  private void ensureSortedOnMz(List<Pair<Double, Double>> raw) {
    for (int i=0; i<raw.size()-1; i++) {
      if (raw.get(i).getLeft() >= raw.get(i+1).getLeft()) {
        System.out.format("%d: %s >= %d: %s\n", i, raw.get(i).toString(), i+1, raw.get(i+1).toString());
        throw new RuntimeException("m/z values not sorted.");
      }
    }
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
    List<LCMSSpectrum> spectrumObjs = new ArrayList<LCMSSpectrum>();

    LCMSXMLParser parser = new LCMSXMLParser();
    if (fileName.endsWith(".mzML")) {
      Iterator<LCMSSpectrum> iter = parser.getIterator(fileName);
      int pulled = 0;
      while (iter.hasNext() && (howManyToValidate == -1 || pulled++ < howManyToValidate)) 
        spectrumObjs.add(iter.next());
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

      // compare the smoothed, and baseline (mz, intensity)s
      Double smoothed_mz = smoothed.getBasePeakMZ();
      Double smoothed_it = smoothed.getBasePeakIntensity();
      Double baseline_mz = raw.getBasePeakMZ();
      Double baseline_it = raw.getBasePeakIntensity();

      // get the errors as mzE and itE and also accumulate them in mzErr and itErr
      mzErr += (mzE = normalizedPcError(smoothed_mz, baseline_mz));
      itErr += (itE = normalizedPcError(smoothed_it, baseline_it));
      List<Pair<Double, Double>> s = smoothed.getIntensities();
      List<Pair<Double, Double>> r = raw.getIntensities();
      // for (int k=0; k<r.size(); k++)
        // System.out.format("\t%.4f\t%.0f\t%.4f\t%.0f\n", s.get(k).getLeft(), s.get(k).getRight(), r.get(k).getLeft(), r.get(k).getRight());
      if (mzE > 0.02 || itE > 0.02) {
        System.out.format("T: %.4f. mz_err: %.2f%% it_err: %.2f%% s_{mz,I}: {%.4f,%.0f} b_{mz,I}: {%.4f,%.0f}\n", raw.getTimeVal(), mzE*100, itE*100, smoothed_mz, smoothed_it, baseline_mz, baseline_it);
      }
    }
    // average out the mz and intensity errors
    mzErr /= howManyToValidate;
    itErr /= howManyToValidate;

    // convert them to percentage values
    mzErr *= 100;
    itErr *= 100;

    // report to user
    System.out.format("%d Timepoints processed. Aggregate error: mz = %.2f%%, intensity = %.2f%%\n", 
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
