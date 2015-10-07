package com.act.lcms;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Comparator;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import java.io.File;

public class MS2 {

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

  class XZ {
    Double time;
    Double intensity;
    public XZ(Double time, Double intensity) {
      this.time = time;
      this.intensity = intensity;
    }
  }

  class YZ {
    Double mz;
    Double intensity;
    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  final static Double MS1_THRESHOLD_IONS = 1000.0;
  final static Double MS2_THRESHOLD_IONS = 100.0;
  final static Double MS1_MZ_TOLERANCE = 0.001;
  final static Double MS2_MZ_TOLERANCE = 10 * MS1_MZ_TOLERANCE;
  final static Double TIME_TOLERANCE = 0.1;
  final static Double MAX_TIME_BW_MS1_AND_2 = 5.0; // 5 seconds
  final static Integer REPORT_TOP_N = 20;

  private List<YZ> getMS1(List<XYZ> spectra, Double time) {
    // Look for precisely this time point, so infinitely small window
    Double tLow = time - TIME_TOLERANCE / 1e3d;
    Double tHigh = time + TIME_TOLERANCE / 1e3d;

    Set<Double> times = new HashSet<>();
    List<YZ> mzInt = new ArrayList<>();
    for (XYZ xyz : spectra) {
      Double timeHere = xyz.time;

      // if not within the time window ignore
      if (timeHere < tLow || timeHere > tHigh)
        continue;
      times.add(timeHere);

      if (xyz.intensity < MS1_THRESHOLD_IONS)
        continue;
      mzInt.add(new YZ(xyz.mz, xyz.intensity));
    }

    if (times.size() > 1) {
      String errmsg = "SEVERE ERR: More than one scan in the MS1 window. Times values: " + times;
      throw new RuntimeException(errmsg);
    }

    return mzInt;
  }

  private List<YZ> getMS2(List<XYZ> spectra, Double time, Double expectMz) {
    // Find the timepoint in [time, time + X] seconds where we find the
    // main peak of the fragmented ions
    List<XYZ> timeInt = new ArrayList<>();
    for (XYZ xyz : spectra) {
      Double timeHere = xyz.time;

      // dont look in the past, and not more than 5 seconds into the
      // future from the MS1 trigger
      if (timeHere < time || timeHere > time + MAX_TIME_BW_MS1_AND_2)
        continue;

      // is the expected main peak is in this time point?
      if (xyz.mz > (expectMz - MS2_MZ_TOLERANCE) && xyz.mz < (expectMz + MS2_MZ_TOLERANCE)) {
        // this is a candidate time point
        timeInt.add(xyz);
      }
    }
    // sort by intensities in descending order
    Collections.sort(timeInt, new Comparator<XYZ>() {
      public int compare(XYZ a, XYZ b) {
        return b.intensity.compareTo(a.intensity);
      }
    });
    XYZ locatedscan = timeInt.get(0);
    System.out.format("In MS2: Expected MS2 peak %f, found %f of intensity %f at time %f (delta: %f from MS1 trigger)\n", expectMz, locatedscan.mz, locatedscan.intensity, locatedscan.time, locatedscan.time - time);

    time = locatedscan.time;

    // Look for precisely this time point, so infinitely small window
    Double tLow = time - TIME_TOLERANCE / 1e3d;
    Double tHigh = time + TIME_TOLERANCE / 1e3d;

    Map<Double, Double> mzTotalIons = new HashMap<>();
    Set<Double> times = new HashSet<>();
    for (XYZ xyz : spectra) {
      Double timeHere = xyz.time;

      // if not within the time window ignore
      if (timeHere < tLow || timeHere > tHigh)
        continue;
      times.add(timeHere);

      Double ions = xyz.intensity + (mzTotalIons.containsKey(xyz.mz) ? mzTotalIons.get(xyz.mz) : 0);
      mzTotalIons.put(xyz.mz, ions);
    }

    if (times.size() > 1) {
      String errmsg = "SEVERE ERR: More than one scan seen in MS2 within 0.5 seconds of the trigger in MS1. Times values in MS2 spectra: " + times;
      System.out.println(errmsg);
      // throw new RuntimeException(errmsg);
    }

    List<YZ> mzIons = new ArrayList<>();
    for (Double mz : mzTotalIons.keySet()) {
      Double totalIonCount = mzTotalIons.get(mz);
      if (totalIonCount < MS2_THRESHOLD_IONS)
        continue;
      mzIons.add(new YZ(mz, totalIonCount));
    }

    // need to sort to output proper set to be plotted by gnuplot
    Collections.sort(mzIons, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return a.mz.compareTo(b.mz);
      }
    });

    return mzIons;
  }

  private Pair<Double, Double> getMaxAndNth(List<YZ> mzInt, int N) {
    if (N > mzInt.size())
      N = mzInt.size();

    List<YZ> mzIonsByInt = new ArrayList<>(mzInt);
    Collections.sort(mzIonsByInt, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return b.intensity.compareTo(a.intensity);
      }
    });
    
    // lets normalize to the largest intensity value we have.
    Double largest = mzIonsByInt.get(0).intensity;
    Double NthLargest = mzIonsByInt.get(N - 1).intensity;

    // print out the top N peaks
    for (int i=0; i<N; i++) {
      YZ yz = mzIonsByInt.get(i);
      System.out.format("mz: %f\t intensity: %.2f%%\n", yz.mz, 100*yz.intensity/largest);
    }
    System.out.println("\n");

    return Pair.of(largest, NthLargest);
  }

  private List<XZ> getSpectraForMz(List<XYZ> spectra, Double mz) {
    List<XZ> spectraForMz = new ArrayList<>();
    System.out.format("For mz: %f, time spectrum:\n", mz);
    for (XYZ xyz: spectra) {
      if (xyz.mz > (mz - MS1_MZ_TOLERANCE) && xyz.mz < (mz + MS1_MZ_TOLERANCE)) {
        if (xyz.intensity < MS1_THRESHOLD_IONS)
          continue;
        spectraForMz.add(new XZ(xyz.time, xyz.intensity));
        System.out.format("%f\t%f\t%f\n", xyz.time, xyz.mz, xyz.intensity);
      }
    }
    System.out.println("\n");
    return spectraForMz;
  }

  private Double getMax(List<XZ> atMzTimeIntensities) {
    Double maxAtTime = 0.0;
    Double maxIntensity = 0.0;
    for (int scan = 0; scan < atMzTimeIntensities.size(); scan++) {
      XZ xz = atMzTimeIntensities.get(scan);
      if (maxIntensity < xz.intensity) {
        maxIntensity = xz.intensity;
        maxAtTime = xz.time;
      }
    }
    return maxAtTime;
  }

  private List<XYZ> getSpectra(Iterator<LCMSSpectrum> spectraIt) {
    List<XYZ> spectra = new ArrayList<>();

    while (spectraIt.hasNext()) {
      LCMSSpectrum timepoint = spectraIt.next();
      Double T = timepoint.getTimeVal();

      // get all (mz, intensity) at this timepoint
      for (Pair<Double, Double> mz_int : timepoint.getIntensities()) {
        double mzHere = mz_int.getLeft();
        double intensity = mz_int.getRight();

        spectra.add(new XYZ(T, mzHere, intensity));
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
    if (args.length < 4 || !areNCFiles(Arrays.copyOfRange(args, 3, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mz for main product, e.g., 431.1341983 (ononin) \n" +
          "(2) mz for main fragment, e.g., 269.0805 (ononin fragment from https://metlin.scripps.edu/metabo_info.php?molid=64295) \n" +
          "(3) prefix for .data and rendered .pdf \n" +
          "(4..) 2 NetCDF .nc files, 01.nc, 02.nc from MSMS run"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    Double mzOfMainFragment = Double.parseDouble(args[1]);
    String outPrefix = args[2];
    String[] netCDFFnames = Arrays.copyOfRange(args, 3, args.length);
    String fmt = "pdf";
    Gnuplotter plotter = new Gnuplotter();

    MS2 c = new MS2();
    List<List<XYZ>> spectra = c.getSpectra(netCDFFnames);

    // the first .nc is the ion trigger on the mz extracted
    List<XYZ> triggerMS1 = spectra.get(0);
    // the second .nc is the MSMS scan
    List<XYZ> fragmentMS2 = spectra.get(1);

    List<XZ> triggerSpectra = c.getSpectraForMz(triggerMS1, mz);
    Double time = c.getMax(triggerSpectra);
    System.out.format("Trigger scan found time: %f seconds (%f minutes)\n", time, time/60);

    List<YZ> ms1PrecursorIons = c.getMS1(triggerMS1, time);
    List<YZ> ms2Spectra = c.getMS2(fragmentMS2, time, mzOfMainFragment);

    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    int count = 0;
    for (List<YZ> yzSlice : new List[] { ms1PrecursorIons, ms2Spectra }) {
      Pair<Double, Double> largestAndNth = c.getMaxAndNth(yzSlice, REPORT_TOP_N);
      Double largest = largestAndNth.getLeft();
      Double nth = largestAndNth.getRight();

      // print out the spectra to outDATA
      for (YZ yz : yzSlice) {

        // threshold to remove everything that is not in the top peaks
        if (yz.intensity < nth)
          continue;

        out.format("%.4f\t%.4f\n", yz.mz, 100*(yz.intensity/largest));
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    plotter.plot2DImpulsesWithLabels(outDATA, outPDF, new String[] { "ms1", "ms2" }, mz, "mz", 105.0, "intensity (%)", fmt);
  }
}
