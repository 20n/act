package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.commons.lang3.tuple.Pair;

public class ExtractFromNetCDFAroundMass {

  /**
   * Extracts a window around a particular m/z value within the spectra
   * @param file The netCDF file with the full LCMS (lockmass corrected) spectra
   * @param mz The m/z value +- 1 around which to extract data for
   * @param numTimepointsToExamine Since we stream the netCDF in, we can choose
   *                               to look at the first few timepoints or -1 for all
   */
  public void get2DWindow(String file, double mz, int numTimepointsToExamine) 
    throws Exception {
    LCMSParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> iter = parser.getIterator(file);
    int pulled = 0;
    Double mzTightL = mz - 0.1;
    Double mzTightR = mz + 0.1;
    Double mzMinus1Da = mz - 1;
    Double mzPlus1Da = mz + 1;

    // iterate through first few, or all if -1 given
    while (iter.hasNext() && (numTimepointsToExamine == -1 || pulled < numTimepointsToExamine)) {
      LCMSSpectrum timepoint = iter.next();

      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // this time point is valid to look at if its max intensity is around
      // the mass we care about. So lets first get the max peak location
      Pair<Double, Double> max_peak = findMaxPeak(intensities);
      Double max_mz = max_peak.getLeft();

      // If the max_mz value is pretty close to our target mass, ie in [mzTightL, mzTightR]
      // Then this timepoint is a good timepoint to output... proceed, shall we.
      if (max_mz >= mzTightL && max_mz <= mzTightR) {

        // For this timepoint, output a window
        for (int k=0; k<intensities.size(); k++) {
          Double mz_here = intensities.get(k).getLeft();
          Double intensity = intensities.get(k).getRight();

          // The window not as tight, but +-1 Da around the target mass
          if (mz_here > mzMinus1Da && mz_here < mzPlus1Da) {
            System.out.format("%d\t%.4f\t%.4f\t%.0f\n", timepoint.getIndex(),
                timepoint.getTimeVal(), mz, intensity);
          }
        }
      }
      pulled++;
    }
  }

  private Pair<Double, Double> findMaxPeak(List<Pair<Double, Double>> raw) {   
    // the intensity is the second value in the pairs
    // this finds the pair with the max intensity 

    Pair<Double, Double> max = null;   
    for (Pair<Double, Double> mz_int : raw) {    
      if (max == null || max.getRight() < mz_int.getRight())   
        max = mz_int;    
    }    
    return max;    
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3 || !args[0].endsWith(".nc")) {
      throw new RuntimeException("Needs (1) NetCDF .nc file, " + 
          "(2) mass value, e.g., 132.0772 for debugging, " +
          "(3) how many timepoints to process (-1 for all)");
    }

    String netCDF = args[0];
    Double mz = Double.parseDouble(args[1]);
    Integer numSpectraToProcess = Integer.parseInt(args[2]);

    new ExtractFromNetCDFAroundMass().get2DWindow(netCDF, mz, numSpectraToProcess);
  }
}
