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
import java.io.FileOutputStream;
import org.apache.commons.lang3.tuple.Pair;

public class MS2 {

  class YZ {
    Double mz;
    Double intensity;
    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  final static Double MS1_MZ_TOLERANCE = 0.001;
  final static Double TIME_TOLERANCE = 0.1 / 1e3d;
  final static Integer REPORT_TOP_N = 20;

  private List<LCMS2MZSelection> filterByTriggerMass(Iterator<LCMS2MZSelection> ms2Scans, Double targetMass) {
    // Look for precisely this time point, so infinitely small window
    Double mzLow = targetMass - MS1_MZ_TOLERANCE;
    Double mzHigh = targetMass + MS1_MZ_TOLERANCE;

    List<LCMS2MZSelection> relevantMS2Scans = new ArrayList<>();
    while (ms2Scans.hasNext()) {
      LCMS2MZSelection scan  = ms2Scans.next();
      Double targetWindow = scan.getIsolationWindowTargetMZ();
      if (targetWindow >= mzLow && targetWindow <= mzHigh) {
        relevantMS2Scans.add(scan);
      }
    }

    if (relevantMS2Scans.size() < 1) {
      String errmsg = String.format("SEVERE ERR: found no matching MS2 scans for MS1 target mass %f", targetMass);
      throw new RuntimeException(errmsg);
    }

    return relevantMS2Scans;
  }

  List<YZ> spectrumToYZList(LCMSSpectrum spectrum) {
    List<YZ> yzList = new ArrayList<>(spectrum.getIntensities().size());
    for (Pair<Double, Double> p : spectrum.getIntensities()) {
      yzList.add(new YZ(p.getLeft(), p.getRight()));
    }
    return yzList;
  }

  List<List<YZ>> getSpectraForMatchingScans(
      List<LCMS2MZSelection> relevantMS2Selections, Iterator<LCMSSpectrum> ms2Spectra) {
    List<List<YZ>> results = new ArrayList<>();

    Iterator<LCMS2MZSelection> selectionIterator = relevantMS2Selections.iterator();
    if (!selectionIterator.hasNext()) {
      // Previous checks should have prevented this.
      throw new RuntimeException("No scans available for spectrum matching");
    }
    LCMS2MZSelection thisSelection = selectionIterator.next();
    // TODO: handle other time units more gracefully.
    if (!"minute".equals(thisSelection.getTimeUnit())) {
      throw new RuntimeException(String.format(
          "Expected 'minute' for MS2 scan selection time unit, but found '%s'", thisSelection.getTimeUnit()));
    }
    Double ms2Time = thisSelection.getTimeVal() * 60.0d; // mzML times tend to be in minutes;
    Double tLow = ms2Time - TIME_TOLERANCE;
    Double tHigh = ms2Time + TIME_TOLERANCE;

    while (ms2Spectra.hasNext()) {
      boolean advanceMS2Selection = false;

      LCMSSpectrum spectrum = ms2Spectra.next();
      if (spectrum.getTimeVal() >= tLow && spectrum.getTimeVal() <= tHigh) {
        // We found a matching scan!
        results.add(this.spectrumToYZList(spectrum));
        advanceMS2Selection = true;
      } else if (spectrum.getTimeVal() > ms2Time) {
        System.err.format("ERROR: found spectrum at time %f when searching for MS2 scan at %f, skipping MS2 scan\n",
          spectrum.getTimeVal(), ms2Time);
        advanceMS2Selection = true;
      } // Otherwise, this spectrum's time doesn't match the time point of the next relevant MS2 scan.  Skip it!

      if (advanceMS2Selection) {
        if (!selectionIterator.hasNext()) {
          // No more relevant scans to search for.
          break;
        }
        thisSelection = selectionIterator.next();
        ms2Time = thisSelection.getTimeVal() * 60.0d; // Assume time units are consistent across all mzML entries.
        tLow = ms2Time - TIME_TOLERANCE;
        tHigh = ms2Time + TIME_TOLERANCE;

      }
    }

    if (selectionIterator.hasNext()) {
      System.err.format("ERROR: ran out of spectra to match against MS2 scans with some scans still unmatched.\n");
    }

    return results;
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
    for (int i = 0; i < N; i++) {
      YZ yz = mzIonsByInt.get(i);
      System.out.format("mz: %f\t intensity: %.2f%%\n", yz.mz, 100*yz.intensity/largest);
    }
    System.out.println("\n");

    return Pair.of(largest, NthLargest);
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
    if (args.length < 4 || !areNCFiles(new String[] {args[3]})) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mz for main product, e.g., 431.1341983 (ononin) \n" +
          "(2) prefix for .data and rendered .pdf \n" +
          "(3) mzML file from MS2 run (to extract trigger masses) \n" +
          "(4) NetCDF .nc file 02.nc from MSMS run"
          );
    }

    Double mz = Double.parseDouble(args[0]);
    String outPrefix = args[1];
    String mzMLFile = args[2];
    String netCDFFile = args[3];
    String fmt = "pdf";
    Gnuplotter plotter = new Gnuplotter();

    MS2 c = new MS2();

    // the first .nc is the ion trigger on the mz extracted
    List<LCMS2MZSelection> matchingScans =
        c.filterByTriggerMass(new LCMS2mzMLParser().getIterator(mzMLFile), mz);

    Iterator<LCMSSpectrum> spectrumIterator = new LCMSNetCDFParser().getIterator(netCDFFile);

    List<List<YZ>> ms2Spectra = c.getSpectraForMatchingScans(matchingScans, spectrumIterator);

    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    List<String> graphNames = new ArrayList<>(ms2Spectra.size());
    for (int i = 0; i < ms2Spectra.size(); i++) {
      graphNames.add(String.format("fragment_%d", i));
    }

    int count = 0;
    for (List<YZ> yzSlice : ms2Spectra) {
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
    // 105.0 here means 105% for the y-range of a [0%:100%] plot. We want to leave some buffer space at
    // at the top, and hence we go a little outside of the 100% max range.
    plotter.plot2DImpulsesWithLabels(outDATA, outPDF, graphNames.toArray(new String[graphNames.size()]), mz + 50.0,
        "mz", 105.0, "intensity (%)", fmt);
  }
}
