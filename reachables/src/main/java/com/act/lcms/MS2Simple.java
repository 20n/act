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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MS2Simple {

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_TARGET_TRIGGER_MZ = "t";
  public static final String OPTION_MZML_FILE = "x";
  public static final String OPTION_NETCDF_FILE = "n";
  public static final String OPTION_MS2_PEAK_SEARCH_MASSES = "m";
  public static final String OPTION_PICK_TOP_N_MATCHES = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "Plots MS2 scans whose trigger (isolation target) mass/charge matches some specified value. ",
      "Accepts an optional list of m/z values that will be used to filter MS2 scans by peak intensity. ",
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
        .desc("(Optional) An ordered, comma separated list of m/z values to use when selecting MS2 scans to plot; " +
            "plots all by default")
        .hasArgs().valueSeparator(',')
        .longOpt("ms2-search-mzs")
    );
    add(Option.builder(OPTION_PICK_TOP_N_MATCHES) // No short option for top-n.
        .argName("Top N matches")
        .desc("(Optional) Only plot the N best matches, ordered by numbered of matching peaks and time; " +
            "only applied if MS2 search m/z values are specified")
        .hasArg()
        .longOpt("top-n")
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

  /* In the MS1 case, we look for a window that we think is within the
   * tolerance of the instrument.  This should also be set on the
   * instrument when doing MS2 runs.  This value attempts to balance
   * meaningful signal detection with noise elimination. */
  final static Double MS1_MZ_TOLERANCE = 0.01;


  // Use the time window to only identify identical scans
  // hence the infinitely small window
  final static Double TIME_TOLERANCE = 0.1 / 1e3d;

  /* Only count peaks that have a non-zero intensity value in case the scan contains a matching (m/z, intensity) pair
   * with a near-zero intensity. */
  final static Double THRESHOLD_SEARCH_MZ_MS2_PEAK_INTENSITY = 0.01;


  private List<LCMS2MZSelection> filterByTriggerMass(Iterator<LCMS2MZSelection> ms2Scans, Double targetMass) {
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

  /* This is a helper function to `findPeaksTriggeredByMZ`. It translates the selected trigger times
   * from the mzML files into scans extracted from the NetCDF files. Trigger times from mzML come
   * in as `minute`s that we convert to seconds, and then look for a scan in the NetCDF file that is
   * infinitely close (TIME_TOLERANCE) to that trigger time. */
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

  /* We are looking for scans triggered by a single `mz` value of interest. The entire MS2 run may
   * contain many trigger events (on different masses possibly--the instrument can be parametrized
   * to trigger on an arbitrary number of trigger masses). Therefore we need to filter out the ones
   * that triggered on our analysis mass here. This function does that. */
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
      if (dist <= MS2_MZ_COMPARE_TOLERANCE) {

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

  /* This is the gatekeeper function deciding whether the given scan matches the fragmentation pattern
   * we expect. The expected fragmentation pattern is specified as a list (ideally going from the most
   * prominent peak downwards) of mz values. Most likely that set of peaks is going to come from
   * reference MS2 patterns (e.g., METLIN or runs of the standards). */
  private Integer searchForMatchingPeaks(List<Double> searchMzs, MS2Collected scan) {
    // once a peak is matched, we should remove it from the available
    Map<Double, Double> matchingPeakIntensities = new HashMap<>();
    for (Double mz: searchMzs) {

      YZ matchInA = getMatchingPeak(mz, scan.ms2);
      if (matchInA != null) {
        // Compare the scan intensity with a tiny threshold to ignore ultra-low-intensity noise.
        if (matchInA.intensity > THRESHOLD_SEARCH_MZ_MS2_PEAK_INTENSITY) {
          // Got a match!
          matchingPeakIntensities.put(mz, matchInA.intensity);
        } // otherwise don't count it as a match.
      }
    }

    // TODO: do better result reporting and maybe consider matches based on an order-weighted score or something.

    // Return the number of peaks that were found to match the search m/z values.
    return matchingPeakIntensities.size();
  }

  private List<Pair<MS2Collected, Integer>> filterByMS2PeakMatch(
      List<Double> ms2SearchMZs, List<MS2Collected> scans, Integer pickTopN) {
    List<Pair<MS2Collected, Integer>> results = new ArrayList<>();
    for (MS2Collected scan : scans) {
      Integer matchCount = searchForMatchingPeaks(ms2SearchMZs, scan);
      if (matchCount > 0) {
        results.add(Pair.of(scan, matchCount));
      }
    }

    Collections.sort(results, new Comparator<Pair<MS2Collected, Integer>>() {
      @Override
      public int compare(Pair<MS2Collected, Integer> o1, Pair<MS2Collected, Integer> o2) {
        if (!o1.getRight().equals(o2.getRight())) {
          // Sort in descending order of match count first.
          return o2.getRight().compareTo(o1.getRight());
        }

        // Fall back to sorting on trigger time (in ascending order) to enforce output stability/reproducability.
        return o1.getLeft().triggerTime.compareTo(o2.getLeft().triggerTime);
      }
    });

    if (pickTopN != null && pickTopN > 0 && pickTopN < results.size()) {
      return results.subList(0, pickTopN);
    }
    return results;
  }

  /* This wrapper function performs the business steps of this class:
   * (1) finds scans triggered by mass we care about
   * (2) filters them to only those scans which have the MS2 peaks we expect (optional, only done if MS2 peaks provided)
   * (3) plots the ms2 scans that survive */
  private void findAndPlotMatchingMS2Scans(Double mz, List<Double> ms2SearchMZs, Integer pickTopN,
                                           String ms2mzML, String ms2nc,
                                           String outPrefix, String fmt) throws IOException {
    List<MS2Collected> ms2Peaks = null;

    try {
      ms2Peaks = findPeaksTriggeredByMZ(mz, ms2mzML, ms2nc);
    } catch (Exception e) {
      System.err.format("Caught exception when finding triggered MS2 scans: %s\n", e.getMessage());
    }

    List<Pair<MS2Collected, Integer>> peakCountPairs = null;
    if (ms2SearchMZs.size() > 0) {
      peakCountPairs = filterByMS2PeakMatch(ms2SearchMZs, ms2Peaks, pickTopN);
    } else if (ms2Peaks != null) {
      // Maps List of MS2 scans at trigger mass -> List of Pair(MS2 scans, null) when no ms2 Peaks are specified.
      peakCountPairs = ms2Peaks.stream().map(ms2 -> Pair.of(ms2, (Integer)null)).collect(Collectors.toList());
    }

    plot(peakCountPairs, mz, ms2SearchMZs, outPrefix, fmt);
  }

  private void plot(
      List<Pair<MS2Collected, Integer>> ms2Spectra, Double mz, List<Double> ms2SearchMZs, String outPrefix, String fmt)
    throws IOException {
    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    List<String> plotID = new ArrayList<>(ms2Spectra.size());
    for (Pair<MS2Collected, Integer> pair: ms2Spectra) {
      MS2Collected yzSlice = pair.getLeft();
      String caption;
      if (ms2SearchMZs != null && ms2SearchMZs.size() > 0) {
        caption = String.format("target: %.4f, time: %.4f, volts: %.4f, %d / %d matches",
            yzSlice.triggerMass, yzSlice.triggerTime, yzSlice.voltage,
            pair.getRight() == null ? 0 : pair.getRight(), ms2SearchMZs.size());
      } else {
        caption = String.format("target: %.4f, time: %.4f, volts: %.4f",
            yzSlice.triggerMass, yzSlice.triggerTime, yzSlice.voltage);
      }
      plotID.add(caption);
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
    /* We intend to plot the fragmentation pattern, and so do not expect to see fragments that are bigger than the
     * original selected molecule.  We truncate the x-range to the specified m/z but since that will make values close
     * to the max hard to see we add a buffer and truncate the plot in the x-range to m/z + 50.0. */
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

    Integer pickTopNMatches = null;
    if (cl.hasOption(OPTION_PICK_TOP_N_MATCHES)) {
      pickTopNMatches = Integer.valueOf(cl.getOptionValue(OPTION_PICK_TOP_N_MATCHES));
    }

    MS2Simple c = new MS2Simple();
    c.findAndPlotMatchingMS2Scans(mz, ms2SearchMasses, pickTopNMatches, ms2mzml, ms2nc, outPrefix, fmt);
  }
}
