package com.act.lcms;

import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MS2Simple {

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_TARGET_TRIGGER_MZ = "t";
  public static final String OPTION_MZML_FILE = "x";
  public static final String OPTION_NETCDF_FILE = "n";
  public static final String OPTION_MS2_PEAK_SEARCH_MASSES = "m";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "Plots MS2 scans whose trigger (isolation target) mass/charge matches some specified value. ",
      "Accepts an optional list of m/z values that will be used to filter MS2 scans by peak intensity. ",
      "Filter values should be in order from highest to lowest expected intensity. Peaks at specified ",
      "m/z values must be at least 1% of the maximum peak in a scan to pass the filtering step. "
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg().required()
        .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_TARGET_TRIGGER_MZ)
        .argName("trigger mass")
        .desc("The trigger (isolation) m/z value to use when extracting MS2 scans (e.g. 431.1341983 [ononin])")
        .hasArg().required()
        .longOpt("trigger-mass")
    );
    add(Option.builder(OPTION_MZML_FILE)
        .argName("mzML file")
        .desc("The mzML file to scan for trigger masses and collision voltages")
        .hasArg().required()
        .longOpt("mzml-file")
    );
    add(Option.builder(OPTION_NETCDF_FILE)
        .argName("NetCDF file")
        .desc("The MS2 NetCDF (*02.nc) file containing scan data to read/plot")
        .hasArg().required()
        .longOpt("netcdf-file")
    );
    add(Option.builder(OPTION_MS2_PEAK_SEARCH_MASSES)
        .argName("Peak search masses")
        .desc("(Optional) an ordered, comma separated list of m/z values to use when selecting MS2 scans to plot; " +
            "plots all by default")
        .hasArgs().valueSeparator(',')
        .longOpt("ms2-search-mzs")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  class YZ {
    Double mz;
    Double intensity;

    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  class MS2Collected {
    Double triggerMass;
    Double triggerTime;
    Double voltage;
    List<YZ> ms2;

    public MS2Collected(Double triggerMass, Double trigTime, Double collisionEv, List<YZ> ms2) {
      this.triggerMass = triggerMass;
      this.triggerTime = trigTime;
      this.ms2 = ms2;
      this.voltage = collisionEv;
    }
  }

  // Rounding upto 2 decimal places should result in pretty 
  // much an identical match on mz in the MS2 spectra
  final static Double MS2_MZ_COMPARE_TOLERANCE = 0.005; 

  // In the MS1 case, we look for a very tight window 
  // because we do not noise to broaden our signal
  final static Double MS1_MZ_TOLERANCE = 0.01;


  // Use the time window to only identify identical scans
  // hence the infinitely small window
  final static Double TIME_TOLERANCE = 0.1 / 1e3d;

  /* Only count peaks that match the specified MS2 search m/z values if they represent a certain fraction of the
   * total scan intensity.  TODO: should this be a faction of the max rather than the total? */
  final static Double THRESHOLD_SEARCH_MZ_MS2_PEAK_INTENSITY = 0.01;


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

  List<MS2Collected> getSpectraForMatchingScans(
      List<LCMS2MZSelection> relevantMS2Selections, Iterator<LCMSSpectrum> ms2Spectra) {
    List<MS2Collected> ms2s = new ArrayList<>();

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
    Double collisionEnergy = thisSelection.getCollisionEnergy(); // assumed in electronvols
    Double tLow = ms2Time - TIME_TOLERANCE;
    Double tHigh = ms2Time + TIME_TOLERANCE;

    while (ms2Spectra.hasNext()) {
      boolean advanceMS2Selection = false;

      LCMSSpectrum spectrum = ms2Spectra.next();
      Double sTime = spectrum.getTimeVal();
      if (sTime >= tLow && sTime <= tHigh) {
        // We found a matching scan!
        MS2Collected ms2 = new MS2Collected(
            thisSelection.getIsolationWindowTargetMZ(), ms2Time, collisionEnergy, this.spectrumToYZList(spectrum));
        ms2s.add(ms2);
        advanceMS2Selection = true;
      } else if (sTime > ms2Time) {
        System.err.format("ERROR: found spectrum at time %f when searching for MS2 scan at %f, skipping MS2 scan\n",
          sTime, ms2Time);
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

    return ms2s;
  }

  private List<MS2Collected> findPeaksTriggeredByMZ(Double mz, String ms2mzML, String ms2nc)
    throws Exception {

    // the first .nc is the ion trigger on the mz extracted
    List<LCMS2MZSelection> matchingScans =
        filterByTriggerMass(new LCMS2mzMLParser().getIterator(ms2mzML), mz);

    Iterator<LCMSSpectrum> spectrumIterator = new LCMSNetCDFParser().getIterator(ms2nc);
    return getSpectraForMatchingScans(matchingScans, spectrumIterator);
  }

  private YZ getMatchingPeak(Double searchMz, List<YZ> matchAgainst) {
      YZ match = null;
    YZ minDistMatch = null;
    for (YZ peak : matchAgainst) {
      Double dist = Math.abs(peak.mz - searchMz);
      // we look for a peak that is within MS2_MZ_COMPARE_TOLERANCE of mz
      if (dist < MS2_MZ_COMPARE_TOLERANCE) {

        // this is a match, make sure it is the only match
        if (match != null) {
          System.out.format("SEVERE: MS2_MZ_COMPARE_TOLERANCE might be too wide. MS2 peak %.4f has >1 matches.\n" + 
              "\tMatch 1: %.4f\t Match 2: %.4f\n", searchMz, match.mz, peak.mz);
        }

        match = peak;
      }

      // bookkeeping for how close it got, in case no matches within precision
      if (minDistMatch == null || Math.abs(minDistMatch.mz - searchMz) > dist) {
        minDistMatch = peak;
      }
    }
    if (match != null) {
      System.out.format("Peak %8.4f - MATCHES -    PEAK: %8.4f (%6.2f%%) at DISTANCE: %.5f\n",
          searchMz, match.mz, match.intensity, Math.abs(match.mz - searchMz));
    } else {
      System.out.format("Peak %8.4f - NO MTCH - CLOSEST: %8.4f (%6.2f%%) at DISTANCE: %.5f\n",
          searchMz, minDistMatch.mz, minDistMatch.intensity, Math.abs(minDistMatch.mz - searchMz));
    }
    return match;
  }

  private boolean searchForMatchingPeaks(List<Double> searchMzs, MS2Collected scan) {
    // we should go through the peaks in descending order of intensity
    // so that get reported to the output in that order
    List<YZ> orderedBms2 = new ArrayList<>(scan.ms2);
    Collections.sort(orderedBms2, new Comparator<YZ>() {
      public int compare(YZ a, YZ b) {
        return b.intensity.compareTo(a.intensity);
      }
    });

    Double maxIntensity = 0.0;
    for (YZ yz : orderedBms2) {
      if (yz.intensity > maxIntensity) {
        maxIntensity = yz.intensity;
      }
    }

    // once a peak is matched, we should remove it from the available
    Map<Double, Double> matchingPeakIntensities = new HashMap<>();
    for (Double mz: searchMzs) {

      YZ matchInA = getMatchingPeak(mz, orderedBms2);
      if (matchInA != null) {
        Double intensityPc = matchInA.intensity;
        Double fractionOfMaxIntensity = intensityPc / maxIntensity;

        if (fractionOfMaxIntensity > THRESHOLD_SEARCH_MZ_MS2_PEAK_INTENSITY) {
          // Got a match!
          matchingPeakIntensities.put(mz, fractionOfMaxIntensity);
        } // otherwise don't count it as a match.
      }
    }

    // TODO: do better result reporting and maybe consider matches based on an order-weighted score or something.

    // Return true only if we've found a suitable peak for every search m/z.
    return matchingPeakIntensities.size() == searchMzs.size();
  }

  private List<MS2Collected> filterByMS2PeakMatch(List<Double> ms2SearchMZs, List<MS2Collected> scans) {
    List<MS2Collected> results = new ArrayList<>();
    for (MS2Collected scan : scans) {
      if (searchForMatchingPeaks(ms2SearchMZs, scan)) {
        results.add(scan);
      }
    }
    return results;
  }

  private void findAndPlotMatchingMS2Scans(Double mz, List<Double> ms2SearchMZs,
                                           String ms2mzML, String ms2nc,
                                           String outPrefix, String fmt) throws IOException {
    List<MS2Collected> ms2Peaks = null;

    try {
      ms2Peaks = findPeaksTriggeredByMZ(mz, ms2mzML, ms2nc);
      System.out.println("MS2 fragmentation trigged on " + mz);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    if (ms2SearchMZs.size() > 0) {
      ms2Peaks = filterByMS2PeakMatch(ms2SearchMZs, ms2Peaks);
    }

    plot(ms2Peaks, mz, outPrefix, fmt);
  }

  private void plot(List<MS2Collected> ms2Spectra, Double mz, String outPrefix, String fmt)
    throws IOException {
    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    List<String> plotID = new ArrayList<>(ms2Spectra.size());
    for (MS2Collected yzSlice : ms2Spectra) {
      plotID.add(String.format("target: %.4f, time: %.4f, volts: %.4f",
          yzSlice.triggerMass, yzSlice.triggerTime, yzSlice.voltage));
      // Compute the total intensity on the fly so we can plot on a percentage scale.
      double maxIntensity = 0.0;
      for (YZ yz : yzSlice.ms2) {
        if (yz.intensity > maxIntensity) {
          maxIntensity = yz.intensity;
        }
      }
      // print out the spectra to outDATA
      for (YZ yz : yzSlice.ms2) {
        out.format("%.4f\t%.4f\n", yz.mz, 100.0 * yz.intensity / maxIntensity);
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
    new Gnuplotter().plot2DImpulsesWithLabels(outDATA, outPDF, plotID.toArray(new String[plotID.size()]), 
        mz + 50.0, "mz", 105.0, "intensity (%)", fmt);
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String fmt = "pdf";
    Double mz;
    try {
      mz = Double.parseDouble(cl.getOptionValue(OPTION_TARGET_TRIGGER_MZ));
    } catch (NumberFormatException e) {
      System.err.format("Trigger mass must be a floating point number: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
      return; // Silences compiler warnings for `mz`.
    }

    String outPrefix = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
    String ms2mzml = cl.getOptionValue(OPTION_MZML_FILE);
    String ms2nc = cl.getOptionValue(OPTION_NETCDF_FILE);

    String[] ms2SearchMassStrings = cl.getOptionValues(OPTION_MS2_PEAK_SEARCH_MASSES);
    List<Double> ms2SearchMasses;
    if (ms2SearchMassStrings != null && ms2SearchMassStrings.length > 0) {
      ms2SearchMasses = new ArrayList<>(ms2SearchMassStrings.length);
      for (String m : ms2SearchMassStrings) {
        Double d;
        try {
          d = Double.parseDouble(m);
        } catch (NumberFormatException e) {
          System.err.format("MS2 search mass must be a comma separated list of floating point numbers: %s\n",
              e.getMessage());
          HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
          System.exit(1);
          return; // Silences compiler warnings for `d`.
        }
        ms2SearchMasses.add(d);
      }
    } else {
      ms2SearchMasses = new ArrayList<>(0); // Use an empty array rather than null for easier logic later.
    }

    MS2Simple c = new MS2Simple();
    c.findAndPlotMatchingMS2Scans(mz, ms2SearchMasses, ms2mzml, ms2nc, outPrefix, fmt);
  }
}
