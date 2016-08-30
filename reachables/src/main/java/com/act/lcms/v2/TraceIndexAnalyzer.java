package com.act.lcms.v2;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.db.analysis.WaveformAnalysis;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TraceIndexAnalyzer {
  private static final Logger LOGGER = LogManager.getFormatterLogger(TraceIndexAnalyzer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String PLOT_FORMAT_EXTENSION = "pdf";

  private static final Double MIN_TIME_THRESHOLD_SECONDS = 15.0;
  private static final Double MIN_INTENSITY_THRESHOLD = 100.0;
  private static final Double MAX_LOG_SNR_THRESHOLD = 1000.0;

  private static final String OPTION_INDEX_PATH = "x";
  private static final String OPTION_PLOT_PREFIX = "p";
  private static final String OPTION_OUTPUT_PATH = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class consumes windowed traces from an LCMS scan files, searching each window for peaks and writing the ",
      "results of its analysis in a large JSON document."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index is stored; must already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_PLOT_PREFIX)
        .argName("plot prefix")
        .desc("A prefix for the plot data files and PDF of the analysis results")
        .hasArg()
        .longOpt("plot-prefix")
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("outputpath")
        .desc("A path where the SNR/time/intensity results per window should be written as JSON")
        .hasArg().required()
        .longOpt("output")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static class AnalysisResult {
    @JsonProperty("mz")
    private Double mz;

    @JsonProperty("peak_intensity")
    private Double peakIntensity;

    @JsonProperty("peak_time")
    private Double peakTime;

    protected AnalysisResult() {

    }

    public AnalysisResult(Double mz, Double peakIntensity, Double peakTime) {
      this.mz = mz;
      this.peakIntensity = peakIntensity;
      this.peakTime = peakTime;
    }

    public Double getMz() {
      return mz;
    }

    protected void setMz(Double mz) {
      this.mz = mz;
    }

    public Double getPeakIntensity() {
      return peakIntensity;
    }

    protected void setPeakIntensity(Double peakIntensity) {
      this.peakIntensity = peakIntensity;
    }

    public Double getPeakTime() {
      return peakTime;
    }

    protected void setPeakTime(Double peakTime) {
      this.peakTime = peakTime;
    }
  }

  public static void main(String[] args) throws Exception {
    org.apache.commons.cli.Options opts = new org.apache.commons.cli.Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }


    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (!rocksDBFile.exists()) {
      System.err.format("Index file at %s does not exist, nothing to analyze", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    LOGGER.info("Starting analysis");

    TraceIndexAnalyzer analyzer = new TraceIndexAnalyzer();
    analyzer.runExtraction(
        rocksDBFile,
        new File(cl.getOptionValue(OPTION_OUTPUT_PATH)),
        cl.hasOption(OPTION_PLOT_PREFIX) ? Optional.of(cl.getOptionValue(OPTION_PLOT_PREFIX)) : Optional.empty()
    );

    LOGGER.info("Done");
  }

  private void runExtraction(File rocksDBFile, File outputFile, Optional<String> maybePlotsPrefix)
      throws RocksDBException, IOException {
    MS1 ms1 = new MS1();
    List<AnalysisResult> results = new ArrayList<>();

    // Extract each target's trace, computing and saving stats as we go.  This should fit in memory no problem.
    Iterator<Pair<Double, List<XZ>>> traceIterator = new TraceIndexExtractor().getIteratorOverTraces(rocksDBFile);
    while (traceIterator.hasNext()) {
      Pair<Double, List<XZ>> targetAndTrace = traceIterator.next();

      String label = String.format("%.6f", targetAndTrace.getLeft());

      // Note: here we cheat by knowing how the MS1 class is going to use this incredibly complex container.
      MS1ScanForWellAndMassCharge scanForWell = new MS1ScanForWellAndMassCharge();
      scanForWell.setMetlinIons(Collections.singletonList(label));
      scanForWell.getIonsToSpectra().put(label, targetAndTrace.getRight());
      Double maxPeakTime = ms1.computeAndStorePeakProfile(scanForWell, label);

      Pair<List<XZ>, Map<Double, Double>> timeWindowsAndMaxes =
          WaveformAnalysis.compressIntensityAndTimeGraphsAndFindMaxIntensityInEveryTimeWindow(
              targetAndTrace.getRight(), 5); // Same as waveform analysis
      List<XZ> calledPeaks = WaveformAnalysis.detectPeaksInIntensityTimeWaveform(
          timeWindowsAndMaxes.getLeft(), 250.0d); // Same as waveform analysis.

      for (XZ calledPeak : calledPeaks) {
        AnalysisResult result = new AnalysisResult(
            targetAndTrace.getLeft(),
            timeWindowsAndMaxes.getRight().get(calledPeak.getTime()),
            calledPeak.getTime()
        );

        results.add(result);
      }
    }

    LOGGER.info("Writing window stats to JSON file");
    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fos, results);
    }

    // Don't use Optional.map here because exceptions.  Sigh.
    if (maybePlotsPrefix.isPresent()) {
      LOGGER.info("Writing plots of LogSNR/max peak intensity over m/z");
      new TraceIndexAnalyzer().plotAnalysisResults(maybePlotsPrefix.get(), results);
    }
  }

  private void plotAnalysisResults(String prefix, List<AnalysisResult> analysisResults) throws IOException {
    File dataFile = new File(prefix + ".data");
    File outputFile = new File(StringUtils.join(Arrays.asList(prefix, PLOT_FORMAT_EXTENSION), '.'));
    File gnuplotFile = new File(StringUtils.join(Arrays.asList(prefix, "gnuplot"), '.'));

    Optional<Double> snrMax, intensityMax, timeMax;
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
      // Pull out the features of each window and write them as tables so we can plot them across the M/Z domain.

      /* Filter out some nonsensical or useless results when producing the graphs, which just add noise to already very
       * busy plots.  Note that these only apply to the plots, not to the results (we can always filter post-analysis).
       *
       * Retention times < 15.0s usually indicate junk that has flowed in with the dead volume, and historically we've
       * been okay ignoring it.  I know we've detected some molecules before this time, but anything that shows up in
       * the first 10 to 20s of a run is highly suspicious.  Our min-threshold is pretty generous and avoids cases
       * where a window might be dividing by zero or something terrible like that.  */
      List<AnalysisResult> filteredResults =
          analysisResults.stream().
              filter(r -> r.getPeakTime() >= MIN_TIME_THRESHOLD_SECONDS). // Anything before is likely dead volume.
              filter(r -> r.getPeakIntensity() >= MIN_INTENSITY_THRESHOLD). // This is well below the noise floor.
              collect(Collectors.toList());

      // Then write the peak intensities.
      filteredResults.stream().
          map(r -> Pair.of(r.getMz(), r.getPeakIntensity())).
          forEach(p -> writeRow(writer, p));
      intensityMax = filteredResults.stream().map(AnalysisResult::getPeakIntensity).max(Double::compare);
      writer.write("\n\n");
    }

    Gnuplotter plotter = new Gnuplotter();
    List<Gnuplotter.PlotConfiguration> plotConfigurations = new ArrayList<Gnuplotter.PlotConfiguration>() {{
      // There will definitely be values present for every Optional max.
      add(new Gnuplotter.PlotConfiguration(Gnuplotter.PlotConfiguration.KIND.GRAPH,
          "Max Intensity", 1, intensityMax.get()));
    }};
    plotter.plot2D(dataFile.getAbsolutePath(), outputFile.getAbsolutePath(), "M/Z", "Intensity",
        PLOT_FORMAT_EXTENSION, null, null, plotConfigurations, gnuplotFile.getAbsolutePath());
  }

  private void writeRow(BufferedWriter writer, Pair<Double, Double> row) throws UncheckedIOException {
    try {
      writer.write(String.format("%.3f\t%.3f\n", row.getLeft(), row.getRight()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
