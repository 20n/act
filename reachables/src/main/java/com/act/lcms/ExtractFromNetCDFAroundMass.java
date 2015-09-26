package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.lang3.tuple.Pair;

public class ExtractFromNetCDFAroundMass {

  public void get2DWindow(String file, double mzDebug, int howManyToValidate) 
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

  private Pair<Double, Double> findMaxPeak(List<Pair<Double, Double>> raw) {   
    Pair<Double, Double> max = null;   
    for (Pair<Double, Double> mz_int : raw) {    
      if (max == null || max.getRight() < mz_int.getRight())   
        max = mz_int;    
    }    
    return max;    
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3 || !args[0].endsWith(".nc")) {
      throw new RuntimeException("Needs (1) NetCDF .nc file, (2) mass value, e.g., 132.0772 for debugging (3) how many timepoints to process (-1 for all)");
    }

    new ExtractFromNetCDFAroundMass().get2DWindow(args[0], Double.parseDouble(args[1]), Integer.parseInt(args[2]));
  }
}
