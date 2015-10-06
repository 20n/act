package com.act.lcms;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

  final static Double THRESHOLD_IONS = 100.0;
  final static Double MZ_TOLERANCE = 0.0001;
  final static Double TIME_TOLERANCE = 0.1;

  private List<YZ> getTotalIonCounts(List<XYZ> spectra, Double time) {
    Map<Double, Double> mzTotalIons = new HashMap<>();
    Double tLow = time - TIME_TOLERANCE;
    Double tHigh = time + TIME_TOLERANCE;
    for (XYZ xyz : spectra) {
      Double timeHere = xyz.time;
      if (timeHere < tLow || timeHere > tHigh)
        continue;
      Double ions = xyz.intensity + (mzTotalIons.containsKey(xyz.mz) ? mzTotalIons.get(xyz.mz) : 0);
      mzTotalIons.put(xyz.mz, ions);
    }

    List<YZ> mzIons = new ArrayList<>();
    for (Double mz : mzTotalIons.keySet()) {
      Double totalIonCount = mzTotalIons.get(mz);
      if (totalIonCount < THRESHOLD_IONS) 
        continue;
      mzIons.add(new YZ(mz, totalIonCount));
    }

    // need to sort to output proper set to be plotted by gnuplot
    Collections.sort(mzIons, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return a.mz.compareTo(b.mz);
      }
    });

    // see if any of our targets are present
    for (YZ yz : mzIons) {
      checkTargetPeak(yz.mz, yz.intensity);
    }

    List<YZ> mzIonsByInt = new ArrayList<>(mzIons);
    Collections.sort(mzIonsByInt, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return b.intensity.compareTo(a.intensity);
      }
    });
    for (int i=0; i<20; i++) {
      YZ yz = mzIonsByInt.get(i);
      System.out.format("mz: %f\t intensity: %f\n", yz.mz, yz.intensity);
    }

    return mzIons;
  }

  private void checkTargetPeak(Double mz, Double ions) {
    Double[] targets = new Double[] {
       77.0392, // intensity 100%
       79.0547, // intensity 27%
       51.0237, // intensity 27%
       44.9978, // intensity 5%
      105.0336, // intensity 5%
       53.0396, // intensity 1%
       29.0134, // intensity 0%
       50.0156, // intensity 0%
       51.0884, // intensity 0%
       78.0470, // intensity 0%
       79.1354  // intensity 0%
    };
    
    for (Double target : targets) {
      if (mz > (target - MZ_TOLERANCE) && mz < (target + MZ_TOLERANCE)) {
        System.out.format("target: %3.4f\t\tdetected: %03.4f\tions: %.0f%%\n", target, mz, 100*ions/22069.515625);
      }
    }
  }

  private List<XZ> getSpectraForMz(List<XYZ> spectra, Double mz) {
    List<XZ> spectraForMz = new ArrayList<>();
    for (XYZ xyz: spectra) {
      if (xyz.mz > (mz - MZ_TOLERANCE) && xyz.mz < (mz + MZ_TOLERANCE)) {
        spectraForMz.add(new XZ(xyz.time, xyz.intensity));
      }
    }
    return spectraForMz;
  }

  private static Double getMax(List<XZ> atMzTimeIntensities) {
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
    if (args.length < 3 || !areNCFiles(Arrays.copyOfRange(args, 2, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mass value, e.g., 123.0440 \n" +
          "(2) prefix for .data and rendered .pdf \n" +
          "(3..) 2 NetCDF .nc files, 01.nc, 02.nc from MSMS run"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    String outPrefix = args[1];
    String[] netCDFFnames = Arrays.copyOfRange(args, 2, args.length);
    String fmt = "pdf";
    Gnuplotter plotter = new Gnuplotter();

    MS2 c = new MS2();
    List<List<XYZ>> spectra = c.getSpectra(netCDFFnames);

    // the first .nc is the ion trigger on the mz extracted
    List<XYZ> triggerMS1 = spectra.get(0);
    // the second .nc is the MSMS scan
    List<XYZ> fragmentMS2 = spectra.get(1);

    List<XZ> triggerSpectra = c.getSpectraForMz(triggerMS1, mz);
    Double time = getMax(triggerSpectra);
    System.out.format("Trigger scan found time: %f seconds (%f minutes)\n", time, time/60);

    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    // print out the spectra to outDATA
    for (XZ xz : triggerSpectra) {
      out.format("%.4f\t%.4f\n", xz.time, xz.intensity);
      out.flush();
    }
    // delimit this dataset from the rest
    out.print("\n\n");

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    plotter.plot2D(outDATA, outPDF, Arrays.copyOfRange(netCDFFnames, 0, 1), mz, -1.0, fmt);

    List<YZ> totalIonCounts = c.getTotalIonCounts(fragmentMS2, time + 1.2);

  }
}
