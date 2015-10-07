package com.act.lcms;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import org.apache.commons.lang3.tuple.Pair;

public class CompareTwoNetCDFAroundMass {

  static final double mzTolerance = 0.01;
  static final int maxNumMzTolerated = 3;

  private double extractMZ(double mzWanted, List<Pair<Double, Double>> intensities, double time) {
    double intensityFound = 0;
    int numWithinPrecision = 0;
    double mzLowRange = mzWanted - mzTolerance;
    double mzHighRange = mzWanted + mzTolerance;
    // we expect there to be pretty much only one intensity value in the precision
    // range we are looking at. But if a lot of masses show up then complain
    for (Pair<Double, Double> mz_int : intensities) {
      double mz = mz_int.getLeft();
      double intensity = mz_int.getRight();


      if (mz > mzLowRange && mz < mzHighRange) {
        intensityFound += intensity;
        numWithinPrecision++;
        if (intensity > 100000)
          System.out.format("time: %f\tmz: %f\t intensity: %f\n", time, mz, intensity);
      }
    }

    if (numWithinPrecision > maxNumMzTolerated) {
      System.out.format("Only expected %d, but found %d in the mz range [%f, %f]\n", maxNumMzTolerated, 
          numWithinPrecision, mzLowRange, mzHighRange);
    }

    return intensityFound;
  }

  private List<Pair<Double, Double>> extractMZSlice(double mz, Iterator<LCMSSpectrum> spectraIt, int numOut, String tagfname) {
    List<Pair<Double, Double>> mzSlice = new ArrayList<>();

    int pulled = 0;
    // iterate through first few, or all if -1 given
    while (spectraIt.hasNext() && (numOut == -1 || pulled < numOut)) {
      LCMSSpectrum timepoint = spectraIt.next();

      // get all (mz, intensity) at this timepoint
      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // this time point is valid to look at if its max intensity is around
      // the mass we care about. So lets first get the max peak location
      double intensityForMz = extractMZ(mz, intensities, timepoint.getTimeVal());

      // the above is Pair(mz_extracted, intensity), where mz_extracted = mz
      // we now add the timepoint val and the intensity to the output
      mzSlice.add(Pair.of(timepoint.getTimeVal(), intensityForMz));

      pulled++;
    }
    System.out.format("Done: %s\n\n", tagfname);

    return mzSlice;
  }

  /**
   * Extracts a window around a particular m/z value within the spectra
   * @param file The netCDF file with the full LCMS (lockmass corrected) spectra
   * @param mz The m/z value +- 1 around which to extract data for
   * @param numTimepointsToExamine Since we stream the netCDF in, we can choose
   *                               to look at the first few timepoints or -1 for all
   */
  public List<List<Pair<Double, Double>>> getSpectraForMass(double mz, String[] fnames, int numTimepointsToExamine) 
    throws Exception {
    List<List<Pair<Double, Double>>> extracted = new ArrayList<>();
    LCMSParser parser = new LCMSNetCDFParser();

    for (String fname : fnames) {
      Iterator<LCMSSpectrum> iter = parser.getIterator(fname);
      List<Pair<Double, Double>> mzSlice = extractMZSlice(mz, iter, numTimepointsToExamine, fname);
      extracted.add(mzSlice);
    }

    return extracted;
  }

  private static boolean areNCFiles(String[] fnames) {
    for (String n : fnames) {
      System.out.println(".nc file = " + n);
      if (!n.endsWith(".nc"))
        return false;
    }
    return true;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 5 || !areNCFiles(Arrays.copyOfRange(args, 3, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mass value, e.g., 132.0772 for debugging, \n" +
          "(2) how many timepoints to process (-1 for all), \n" +
          "(3) prefix for .data and rendered .pdf \n" +
          "(4,5..) 2 or more NetCDF .nc files"
          );
    }

    String fmt = "pdf";
    Double mz = Double.parseDouble(args[0]);
    Integer numSpectraToProcess = Integer.parseInt(args[1]);
    String outPrefix = args[2];
    String outPDF = outPrefix.equals("-") ? null : outPrefix + "." + fmt;
    String outDATA = outPrefix.equals("-") ? null : outPrefix + ".data";

    CompareTwoNetCDFAroundMass c = new CompareTwoNetCDFAroundMass();
    String[] netCDF_fnames = Arrays.copyOfRange(args, 3, args.length);
    List<List<Pair<Double, Double>>> spectra = c.getSpectraForMass(mz, netCDF_fnames, numSpectraToProcess);

    // Write data output to outfile
    PrintStream out = outDATA == null ? System.out : new PrintStream(new FileOutputStream(outDATA));

    // print out the spectra to outDATA
    for (List<Pair<Double, Double>> spectraInFile : spectra) {
      for (Pair<Double, Double> xy : spectraInFile) {
        out.format("%.4f\t%.4f\n", xy.getLeft(), xy.getRight());
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }
    // find the ymax across all spectra, so that we can have a uniform y scale
    Double yrange = 0.0;
    List<Double> ymaxes = new ArrayList<>();
    for (List<Pair<Double, Double>> spectraInFile : spectra) {
      Double ymax = 0.0;
      for (Pair<Double, Double> xy : spectraInFile) {
        Double intensity = xy.getRight();
        if (ymax < intensity) ymax = intensity;
      }
      ymaxes.add(ymax);
      if (yrange < ymax) yrange = ymax;
    }
    Collections.sort(ymaxes);
    // instead of the max, lets pick the 2nd largest, the std is usually the largest and 
    // is typically a very strong outlier. so 2nd largest ends up showing more detail
    yrange = ymaxes.get(ymaxes.size() - 2);

    if (outDATA != null) {
      // if outDATA is != null, then we have written to .data file
      // now render the .data to the corresponding .pdf file

      // first close the .data
      out.close();

      // render outDATA to outPDF using gnuplo
      Gnuplotter plotter = new Gnuplotter();
      plotter.plot2D(outDATA, outPDF, netCDF_fnames, mz, yrange, fmt);
    }
  }
}
