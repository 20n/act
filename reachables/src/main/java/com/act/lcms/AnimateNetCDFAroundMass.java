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
import java.io.File;

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

  private List<XYZ> getSpectra(Iterator<LCMSSpectrum> spectraIt, Double time, Double tWin, Double mz, 
      Double mzWin) {
    double mzLow = mz - mzWin;
    double mzHigh = mz + mzWin;
    double timeLow = time - tWin;
    double timeHigh = time + tWin;

    List<XYZ> spectra = new ArrayList<>();

    while (spectraIt.hasNext()) {
      LCMSSpectrum timepoint = spectraIt.next();

      if (timepoint.getTimeVal() < timeLow || timepoint.getTimeVal() > timeHigh)
        continue;

      // get all (mz, intensity) at this timepoint
      for (Pair<Double, Double> mz_int : timepoint.getIntensities()) {
        double mzHere = mz_int.getLeft();
        double intensity = mz_int.getRight();

        if (mzHere < mzLow || mzHere > mzHigh)
          continue;

        spectra.add(new XYZ(timepoint.getTimeVal(), mzHere, intensity));
      }
    }

    return spectra;
  }

  public List<List<XYZ>> getSpectra(String[] fnames, Double time, Double tWin, Double mz, 
      Double mzWin) throws Exception {
    List<List<XYZ>> extracted = new ArrayList<>();
    LCMSParser parser = new LCMSNetCDFParser();

    for (String fname : fnames) {
      Iterator<LCMSSpectrum> iter = parser.getIterator(fname);
      extracted.add(getSpectra(iter, time, tWin, mz, mzWin));
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
    if (args.length < 7 || !areNCFiles(Arrays.copyOfRange(args, 5, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mass value, e.g., 132.0772 \n" +
          "(2) time value, e.g., 39.2, (seconds), \n" +
          "(3) minimum Mz Precision, 0.04 \n" +
          "(4) max z axis, e.g., 20000 \n" +
          "(5) prefix for .data and rendered .pdf \n" +
          "(6..) 2 or more NetCDF .nc files"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    Double time = Double.parseDouble(args[1]);
    Double minMzPrecision = Double.parseDouble(args[2]);
    Double maxZAxis = Double.parseDouble(args[3]);
    String outPrefix = args[4];

    // the mz values go from 50-950, we start with a big window and exponentially narrow down
    double mzWin = 100;
    // time values go from 0-450, we start with a big window and exponentially narrow down
    double timeWin = 50;

    // the factor by which to zoom in every step (has to be >1, a value of 2 is good)
    double factor = 1.2;

    // the animation frame count
    int frame = 1;

    AnimateNetCDFAroundMass c = new AnimateNetCDFAroundMass();
    String[] netCDFFnames = Arrays.copyOfRange(args, 5, args.length);
    List<List<XYZ>> spectra = c.getSpectra(netCDFFnames, time, timeWin, mz, mzWin);

    for (List<XYZ> s : spectra) {
      System.out.format("%d xyz datapoints in (initial narrowed) spectra\n", s.size());
    }

    String[] labels = new String[netCDFFnames.length];
    for (int i=0; i<labels.length; i++)
      labels[i] = "Dataset: " + i;
    // you could set labels to netCDFFnames to get precise labels on the graphs

    Gnuplotter plotter = new Gnuplotter();
    String fmt = "png";

    List<String> outImgFiles = new ArrayList<>(), outDataFiles = new ArrayList<>();
    while (mzWin > minMzPrecision) {

      // exponentially narrow windows down
      mzWin /= factor;
      timeWin /= factor;

      List<List<XYZ>> windowedSpectra = c.getSpectraInWindowAll(spectra, time, timeWin, mz, mzWin);

      String frameid = String.format("%3d", frame);
      String outPDF = outPrefix + frameid + "." + fmt;
      String outDATA = outPrefix + frameid + ".data";
      outImgFiles.add(outPDF); 
      outDataFiles.add(outDATA);
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

      // render outDATA to outPDF using gnuplot
      plotter.plotMulti3D(outDATA, outPDF, fmt, labels, maxZAxis);
    }

    plotter.makeAnimatedGIF(outImgFiles, outPrefix + ".gif");
    // all the frames are now in the animated gif, remove the intermediate files
    for (String f: outDataFiles) 
      new File(f).delete();
    for (String f: outImgFiles) 
      new File(f).delete();
  }
}
