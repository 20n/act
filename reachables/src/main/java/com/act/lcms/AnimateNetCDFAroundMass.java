package com.act.lcms;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import org.apache.commons.lang3.tuple.Pair;

public class AnimateNetCDFAroundMass {
  class XYZ {
    Double time;
    Double mz;
    Double intensity;
    public XYZ(Double time, Double mz, Double intensity) {
      this.time = time;
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  private List<List<XYZ>> getSpectraInWindowAll(List<List<XYZ>> spectra, Double time, Double tWin, 
      Double mz, Double mzWin) {
    List<List<XYZ>> windowOnMultiple = new ArrayList<>();
    for (List<XYZ> one : spectra)
      windowOnMultiple.add(getSpectraInWindow(one, time, tWin, mz, mzWin));
    return windowOnMultiple;
  }

  final static int gridSize = 100;

  private List<XYZ> getSpectraInWindow(List<XYZ> spectra, Double time, Double tWin, Double mz, Double mzWin) {
    double mzLow = mz - mzWin;
    double mzHigh = mz + mzWin;
    double timeLow = time - tWin;
    double timeHigh = time + tWin;

    Map<Pair<Long, Long>, List<Double>> gridVals = new HashMap<>();
    // initialize the grid to empty intensity data points 
    for (long i = 0; i <= gridSize; i++) {
      for (long j = 0; j <= gridSize; j++) {
        gridVals.put(Pair.of(i, j), new ArrayList<>());
      }
    }

    // what is the step size between x -> x+1 and y -> y+1
    double timeStep = (2 * tWin) / gridSize;
    double mzStep = (2 * mzWin) / gridSize;

    // for each x,y,z find the grid position it lies in
    // (if it indeed is within the grid window we care)
    // and add that to the data points we see within
    for (XYZ xyz : spectra) {
      // ignore if outside the window
      if (xyz.mz < mzLow || xyz.mz > mzHigh)
        continue;
      if (xyz.time < timeLow || xyz.time > timeHigh)
        continue;

      // if inside the window find the grid position
      long timeGridPos = Math.round((xyz.time - timeLow) / timeStep);
      long mzGridPos = Math.round((xyz.mz - mzLow) / mzStep);
      Pair<Long, Long> xy = Pair.of(timeGridPos, mzGridPos);

      gridVals.get(xy).add(xyz.intensity);
    }

    // average out all the z values that appear at each x,y
    Map<Pair<Long, Long>, Double> gridAvg = new HashMap<>();
    for (Pair<Long, Long> xy : gridVals.keySet()) {
      Double avg = 0.0;
      List<Double> intensities = gridVals.get(xy);
      for (Double intensity : intensities)
        avg += intensity;
      if (!intensities.isEmpty()) 
        avg /= intensities.size();
      gridAvg.put(xy, avg);
    }

    // convert back to list of x,y,z coordinates
    List<XYZ> grid = new ArrayList<>();
    for (long i = 0; i <= gridSize; i++) {
      for (long j = 0; j <= gridSize; j++) {
        Double t = timeLow + i * timeStep;
        Double m = mzLow + j * mzStep;
        Double it = gridAvg.get(Pair.of(i, j));
        grid.add(new XYZ(t, m, it));
      }
    }

    return grid;
  }

  private List<XYZ> getSpectra(Iterator<LCMSSpectrum> spectraIt) {
    List<XYZ> spectra = new ArrayList<>();

    while (spectraIt.hasNext()) {
      LCMSSpectrum timepoint = spectraIt.next();

      // get all (mz, intensity) at this timepoint
      for (Pair<Double, Double> mz_int : timepoint.getIntensities()) {
        spectra.add(new XYZ(timepoint.getTimeVal(), mz_int.getLeft(), mz_int.getRight()));
      }
    }

    return spectra;
  }

  public List<List<XYZ>> getSpectra(String[] fnames) throws Exception {
    List<List<XYZ>> extracted = new ArrayList<>();
    LCMSParser parser = new LCMSNetCDFParser();

    for (String fname : fnames) {
      Iterator<LCMSSpectrum> iter = parser.getIterator(fname);
      extracted.add(getSpectra(iter));
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
    if (args.length < 6 || !areNCFiles(Arrays.copyOfRange(args, 4, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mass value, e.g., 132.0772 \n" +
          "(2) time value, e.g., 39.2, (seconds), \n" +
          "(3) minimum Mz Precision \n" +
          "(4) prefix for .data and rendered .pdf \n" +
          "(5..) 2 or more NetCDF .nc files"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    Double time = Double.parseDouble(args[1]);
    Double minMzPrecision = Double.parseDouble(args[2]);
    String outPrefix = args[3];

    AnimateNetCDFAroundMass c = new AnimateNetCDFAroundMass();
    String[] netCDFFnames = Arrays.copyOfRange(args, 4, args.length);
    List<List<XYZ>> spectra = c.getSpectra(netCDFFnames);

    for (List<XYZ> s : spectra) {
      System.out.format("%d xyz datapoints in spectra\n", s.size());
    }

    // the mz values go from 50-950, we start with a big window and exponentially narrow down
    double mzWin = 100;
    // time values go from 0-450, we start with a big window and exponentially narrow down
    double timeWin = 50;

    double factor = 2;

    // since loop runs mzWin > minMzPrecision, and mzWin/2 in each iteration
    // mzWin/minMzPrecision = 2^numFrames => ln(mzWin/minMzPrecision) = numFrames
    long numFrames = 1 + Math.round(Math.log(mzWin / minMzPrecision) / Math.log(2));
    System.out.println("Num frames: " + numFrames);

    double maxIntensityInFirstFrame = getMaxIntensity(c.getSpectraInWindowAll(spectra, time, timeWin / 2, mz, mzWin / 2));
    double lastDivisor = Math.pow(2, numFrames); // use left shifting?
    double maxIntensityInLastFrame = getMaxIntensity(c.getSpectraInWindowAll(spectra, time, timeWin / lastDivisor, mz, mzWin / lastDivisor));

    double maxZAxis = maxIntensityInFirstFrame;
    double zAxisStep = (maxIntensityInFirstFrame - maxIntensityInLastFrame) / numFrames;
    if (zAxisStep < 0) {
      // if intensity does not decrease let gnuplot scale it
      maxZAxis = -1;
    }

    System.out.format("Z axis: [%.2f, %.2f] in steps of -%.2f\n", maxIntensityInFirstFrame, maxIntensityInLastFrame, zAxisStep);

    // the animation frame count
    int frame = 1;

    while (mzWin > minMzPrecision) {

      // exponentially narrow windows down
      mzWin /= 2;
      timeWin /= 2;

      List<List<XYZ>> windowedSpectra = c.getSpectraInWindowAll(spectra, time, timeWin, mz, mzWin);

      String outPDF = outPrefix + frame + ".pdf";
      String outDATA = outPrefix + frame + ".data";
      frame++;

      // Write data output to outfile
      PrintStream out = new PrintStream(new FileOutputStream(outDATA));

      // print out the spectra to outDATA
      for (List<XYZ> windowOfSpectra : windowedSpectra) {
        for (XYZ xyz : windowOfSpectra) {
          out.format("%.4f\t%.4f\t%.4f\n", xyz.time, xyz.mz, xyz.intensity);
          out.flush();
        }
        // delimit this dataset from the rest
        out.print("\n\n");
      }

      // close the .data
      out.close();

      // render outDATA to outPDF using gnuplo
      Gnuplotter plotter = new Gnuplotter();
      plotter.plotMulti3D(outDATA, outPDF, netCDFFnames, maxZAxis);
    }
  }

  private static double getMaxIntensity(List<List<XYZ>> datasets) {
    double max = 0.0;
    for (List<XYZ> spectra : datasets) 
      for (XYZ dp : spectra)
        if (max < dp.intensity)
          max = dp.intensity;
    return max;
  }
}
