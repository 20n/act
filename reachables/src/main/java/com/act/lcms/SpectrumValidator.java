package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.lang3.tuple.Pair;

import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.UnivariateFunction;

public class SpectrumValidator {

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

  private Pair<Double, Double> getMeanStddev(List<Pair<Double, Double>> raw, boolean removeMax) {
    Double deltaMean = 0.0;
    Double deltaMax = -1.0;
    for (int i=0; i<raw.size()-1; i++) {
      Double thisOne = raw.get(i).getLeft();
      Double nextOne = raw.get(i+1).getLeft();
      Double delta = nextOne - thisOne;

      deltaMax = delta > deltaMax ? delta : deltaMax;

      if (delta < 0.0) {
        System.out.format("%d: %s >= %d: %s\n", i, raw.get(i).toString(), i+1, raw.get(i+1).toString());
        throw new RuntimeException("m/z values not sorted.");
      }

      deltaMean += delta;
    }
    if (removeMax) deltaMean -= deltaMax;
    deltaMean /= (raw.size() - 1);

    Double deltaVar = 0.0;
    for (int i=0; i<raw.size()-1; i++) {
      Double thisOne = raw.get(i).getLeft();
      Double nextOne = raw.get(i+1).getLeft();
      Double delta = nextOne - thisOne;
      deltaVar += (delta - deltaMean) * (delta - deltaMean);
    }
    if (removeMax) deltaVar -= (deltaMax - deltaMean) * (deltaMax - deltaMean);
    deltaVar /= (raw.size() - 1);

    Double deltaStddev = Math.sqrt(deltaVar);

    return Pair.of(deltaMean, deltaStddev);
  }
  

  private Pair<Double, Double> findMaxPeak(List<Pair<Double, Double>> raw) {
    Pair<Double, Double> max = null;
    for (Pair<Double, Double> mz_int : raw) {
      if (max == null || max.getRight() < mz_int.getRight())
        max = mz_int;
    }
    return max;
  }

  private LCMSSpectrum smooth(LCMSSpectrum raw) {
    List<Pair<Double, Double>> intensities = spline(raw.getIntensities());
    Pair<Double, Double> maxPeak = findMaxPeak(intensities);
    Double basePeakMZ = maxPeak.getLeft(), basePeakIntensity = maxPeak.getRight();

    LCMSSpectrum smoothedTimeSpecta = new LCMSSpectrum(raw.getIndex(), raw.getTimeVal(), raw.getTimeUnit(), intensities,
        basePeakMZ, basePeakIntensity, raw.getFunction(), raw.getScan(), null);
    return smoothedTimeSpecta;
  }

  private Pair<Double, Double> validateUsingInstrumentsBasePeaks(LCMSSpectrum smoothed, LCMSSpectrum raw, 
      double mzDebug) {

    // compare the smoothed, and baseline (mz, intensity)s
    Double smoothed_mz = smoothed.getBasePeakMZ();
    Double smoothed_it = smoothed.getBasePeakIntensity();
    Double baseline_mz = raw.getBasePeakMZ();
    Double baseline_it = raw.getBasePeakIntensity();

    // get the errors as mzE and itE and also accumulate them in mzErr and itErr
    Double mzE = normalizedPcError(smoothed_mz, baseline_mz);
    Double itE = normalizedPcError(smoothed_it, baseline_it);
    List<Pair<Double, Double>> s = smoothed.getIntensities();
    List<Pair<Double, Double>> r = raw.getIntensities();
    Double mzDebugL = mzDebug - 0.01;
    Double mzDebugH = mzDebug + 0.01;
    Double mzDebugLL = mzDebug - 0.1;
    Double mzDebugHH = mzDebug + 0.1;
    if (baseline_mz >= mzDebugL && baseline_mz <= mzDebugH) {
      for (int k=0; k<r.size(); k++) {
        if (r.get(k).getLeft() > mzDebugLL && r.get(k).getLeft() < mzDebugHH) {
          System.out.format("\t%.4f\t%.0f\n", r.get(k).getLeft(), r.get(k).getRight());
        }
      }

      // lets get stats on deltas without the base peak's delta included
      // the assumption is that the base peak was artificially removed from
      // the dataset. so lets get stats of what is the "norm" without it.
      boolean removeMax = true; 
      // get some stats on m/z distributions in this timepoint for info
      Pair<Double, Double> stats = getMeanStddev(r, removeMax);
      Double mean_mz = stats.getLeft(), stddev_mz = stats.getRight();

      System.out.format("T{time=%.4f fn=%d scan=%d}: mz_err: %.2f%% it_err: %.2f%% s_{mz,I}: {%.4f,%.0f} b_{mz,I}: {%.4f,%.0f} mean_mz: %.5f stddev_mz: %.5f\n", raw.getTimeVal(), raw.getFunction(), raw.getScan(), mzE*100, itE*100, smoothed_mz, smoothed_it, baseline_mz, baseline_it, mean_mz, stddev_mz);
    }

    return Pair.of(mzE, itE);
  }

  private Double normalizedPcError(Double val, Double baseline) {
    // computes the absolute val error of `val` against the `baseline` as a % (btwn 0,1) of baseline)
    Double error = Math.abs(val - baseline);
    return error/Math.abs(baseline);
  }

  public void validateMzML(String mzMLFile, double mzDebug, int howManyToValidate) 
    throws Exception {
    LCMSParser parser = new LCMSXMLParser();
    Iterator<LCMSSpectrum> iter = parser.getIterator(mzMLFile);
    int pulled = 0;
    Double mzErr = 0.0, itErr = 0.0;
    while (iter.hasNext() && (howManyToValidate == -1 || pulled < howManyToValidate)) {
      LCMSSpectrum timepoint = iter.next();
      // run smoothing over this timepoint and get error percentages by comparing the smoothing output
      // against the basePeak{MZ, Intensity} already present in the mzML from the instrument
      LCMSSpectrum smoothed = smooth(timepoint);

      Pair<Double, Double> err = validateUsingInstrumentsBasePeaks(smoothed, timepoint, mzDebug);

      // aggreate the errors across 
      mzErr += err.getLeft();
      itErr += err.getRight();
      pulled++;
    }
    // average out the mz and intensity errors
    mzErr /= pulled;
    itErr /= pulled;

    // convert them to percentage values
    mzErr *= 100;
    itErr *= 100;

    // report to user
    System.out.format("%d Timepoints processed. Aggregate error: mz = %.2f%%, intensity = %.2f%%\n", 
        pulled, mzErr, itErr);
  }

  public void validateNetCDF(String file, double mzDebug, int howManyToValidate) 
    throws Exception {
    LCMSParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> iter = parser.getIterator(file);
    int pulled = 0;
    Double mzDebugL = mzDebug - 0.1;
    Double mzDebugH = mzDebug + 0.1;
    Double mzDebugLL = mzDebug - 1;
    Double mzDebugHH = mzDebug + 1;

    while (iter.hasNext() && (howManyToValidate == -1 || pulled < howManyToValidate)) {
      LCMSSpectrum timepoint = iter.next();

      List<Pair<Double, Double>> intensities = timepoint.getIntensities();
      Pair<Double, Double> max_peak = findMaxPeak(intensities);
      Double max_mz = max_peak.getLeft(), max_intensity = max_peak.getRight();

      if (max_mz >= mzDebugL && max_mz <= mzDebugH) {
        for (int k=0; k<intensities.size(); k++) {
          Double mz = intensities.get(k).getLeft();
          Double intensity = intensities.get(k).getRight();
          if (mz > mzDebugLL && mz < mzDebugHH) {
            System.out.format("%d\t%.4f\t%.4f\t%.0f\n", timepoint.getIndex(), timepoint.getTimeVal(), mz, intensity);
          }
        }
      }

      pulled++;
    }

  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3 || !args[0].endsWith(".nc")) {
      throw new RuntimeException("Needs (1) NetCDF .nc file, (2) mass value, e.g., 132.0772 for debugging (3) how many timepoints to process (-1 for all)");
    }

    new SpectrumValidator().validateNetCDF(args[0], Double.parseDouble(args[1]), Integer.parseInt(args[2]));
  }
}
