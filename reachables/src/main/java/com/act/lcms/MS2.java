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

  class YZ {
    Double mz;
    Double intensity;
    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  final static Double THRESHOLD_IONS = 100.0;
  final static Double MZ_TOLERANCE = 0.001;

  private List<YZ> getTotalIonCounts(List<XYZ> spectra) {
    Map<Double, Double> mzTotalIons = new HashMap<>();
    for (XYZ xyz : spectra) {
      Double ions = xyz.intensity + (mzTotalIons.containsKey(xyz.mz) ? mzTotalIons.get(xyz.mz) : 0);
      mzTotalIons.put(xyz.mz, ions);
    }

    List<YZ> mzIons = new ArrayList<>();
    for (Double mz : mzTotalIons.keySet()) {
      Double totalIonCount = mzTotalIons.get(mz);
      if (totalIonCount < THRESHOLD_IONS) 
        continue;
      checkTargetPeak(mz, totalIonCount, max);
      mzIons.add(new YZ(mz, totalIonCount));
    }
    Collections.sort(mzIons, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return a.mz.compareTo(b.mz);
      }
    });

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

  private List<List<YZ>> getTotalIonCountsMultiple(List<List<XYZ>> spectras) {
    List<List<YZ>> ionSpectras = new ArrayList<>();
    for (List<XYZ> s : spectras) 
      ionSpectras.add(getTotalIonCounts(s));
    return ionSpectras;
  }

  private List<XYZ> getSpectra(Iterator<LCMSSpectrum> spectraIt) {
    List<XYZ> spectra = new ArrayList<>();

    while (spectraIt.hasNext()) {
      LCMSSpectrum timepoint = spectraIt.next();

      // get all (mz, intensity) at this timepoint
      for (Pair<Double, Double> mz_int : timepoint.getIntensities()) {
        double mzHere = mz_int.getLeft();
        double intensity = mz_int.getRight();

        spectra.add(new XYZ(timepoint.getTimeVal(), mzHere, intensity));
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
          "(1) mass value, e.g., 132.0772 \n" +
          "(2) prefix for .data and rendered .pdf \n" +
          "(3..) 2 or more NetCDF .nc files"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    String outPrefix = args[1];
    String[] netCDFFnames = Arrays.copyOfRange(args, 2, args.length);

    MS2 c = new MS2();
    List<List<XYZ>> spectra = c.getSpectra(netCDFFnames);

    String fmt = "pdf";

    List<List<YZ>> totalIonCounts = c.getTotalIonCountsMultiple(spectra);

    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    // print out the spectra to outDATA
    for (List<YZ> ionCounts : totalIonCounts) {
      for (YZ yz : ionCounts) {
        out.format("%.4f\t%.4f\n", yz.mz, yz.intensity);
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    Gnuplotter plotter = new Gnuplotter();
    plotter.plot2D(outDATA, outPDF, netCDFFnames, mz, -1.0);

  }
}
