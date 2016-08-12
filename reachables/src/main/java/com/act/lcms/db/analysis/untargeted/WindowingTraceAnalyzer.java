package com.act.lcms.db.analysis.untargeted;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class WindowingTraceAnalyzer {
  private static final Logger LOGGER = LogManager.getFormatterLogger(WindowingTraceAnalyzer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String OPTION_INDEX_PATH = "x";
  private static final String OPTION_PLOT_PREFIX = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class consumes windowed traces from an LCMS scan files, searching each window for peaks."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index is be stored; must already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_PLOT_PREFIX)
        .argName("plot prefix")
        .desc("A prefix for the plot data files and PDF of the analysis results")
        .hasArg().required()
        .longOpt("plot-prefix")
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

  public static class WindowAnalysisResult {
    @JsonProperty("min_mz")
    private Double minMz;

    @JsonProperty("max_mz")
    private Double maxMz;

    @JsonProperty("log_snr")
    private Double logSnr;

    @JsonProperty("peak_intensity")
    private Double peakIntensity;

    @JsonProperty("peak_time")
    private Double peakTime;

    protected WindowAnalysisResult() {

    }

    public WindowAnalysisResult(Double minMz, Double maxMz, Double logSnr, Double peakIntensity, Double peakTime) {
      this.minMz = minMz;
      this.maxMz = maxMz;
      this.logSnr = logSnr;
      this.peakIntensity = peakIntensity;
      this.peakTime = peakTime;
    }

    public Double getMinMz() {
      return minMz;
    }

    protected void setMinMz(Double minMz) {
      this.minMz = minMz;
    }

    public Double getMaxMz() {
      return maxMz;
    }

    protected void setMaxMz(Double maxMz) {
      this.maxMz = maxMz;
    }

    public Double getLogSnr() {
      return logSnr;
    }

    protected void setLogSnr(Double logSnr) {
      this.logSnr = logSnr;
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
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }


    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (!rocksDBFile.exists()) {
      System.err.format("Index file at %s does not exist, nothing to analyze", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    MS1 ms1 = new MS1();
    WindowingTraceExtractor extractor = new WindowingTraceExtractor();

    Iterator<Pair<Pair<Double, Double>, List<XZ>>> traceIterator = extractor.getIteratorOverTraces(rocksDBFile);

    List<WindowAnalysisResult> results = new ArrayList<>();

    while (traceIterator.hasNext()) {
      Pair<Pair<Double, Double>, List<XZ>> rangeAndTrace = traceIterator.next();

      String label = String.format("%.3f-%.3f", rangeAndTrace.getLeft().getLeft(), rangeAndTrace.getLeft().getRight());

      // Note: here we cheat by knowing how the MS1 class is going to use this incredibly complex container.
      MS1ScanForWellAndMassCharge scanForWell = new MS1ScanForWellAndMassCharge();
      scanForWell.setMetlinIons(Collections.singletonList(label));
      scanForWell.getIonsToSpectra().put(label, rangeAndTrace.getRight());
      Double maxPeakTime = ms1.computeStats(scanForWell, label);

      WindowAnalysisResult result = new WindowAnalysisResult(
          rangeAndTrace.getLeft().getLeft(), rangeAndTrace.getLeft().getRight(),
          scanForWell.getLogSNRForIon(label),
          scanForWell.getMaxIntensityForIon(label),
          maxPeakTime
      );

      results.add(result);

      //String json = OBJECT_MAPPER.writeValueAsString(result);
      //LOGGER.info(json);
    }

    new WindowingTraceAnalyzer().plotAnalysisResults(cl.getOptionValue(OPTION_PLOT_PREFIX), results);

  }

  private static final String PLOT_FORMAT_EXTENSION = "pdf";

  public void plotAnalysisResults(String prefix, List<WindowAnalysisResult> analysisResults) throws IOException {
    File dataFile = new File(prefix + ".data");
    File outputFile = new File(StringUtils.join(Arrays.asList(prefix, PLOT_FORMAT_EXTENSION), '.'));
    File gnuplotFile = new File(StringUtils.join(Arrays.asList(prefix, "gnuplot"), '.'));

    Optional<Double> snrMax, intensityMax, timeMax;
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
      // Pull out the features of each window and write them as tables so we can plot them across the M/Z domain.

      // First write the log SNRs.
      analysisResults.stream().filter(r -> r.getPeakTime() > 25.0).
          map(r -> Pair.of(WindowingTraceExtractor.windowCenterFromMin(r.getMinMz()),
              Double.min(1000.0, Double.max(0.00, r.getLogSnr())))).
          forEach(p -> writeRow(writer, p));
      snrMax = analysisResults.stream().map(WindowAnalysisResult::getLogSnr).max(Double::compare).map(x -> Double.min(x, 1000.0));
      writer.write("\n\n");

      // Then write the peak intensities.
      analysisResults.stream().filter(r -> r.getPeakTime() > 25.0).
          map(r -> Pair.of(WindowingTraceExtractor.windowCenterFromMin(r.getMinMz()), r.getPeakIntensity())).
          forEach(p -> writeRow(writer, p));
      intensityMax =
          analysisResults.stream().map(WindowAnalysisResult::getPeakIntensity).max(Double::compare);
      writer.write("\n\n");

      // Then write the peak retention times, which will make the least sense.
      analysisResults.stream().filter(r -> r.getPeakTime() > 25.0).
          map(r -> Pair.of(WindowingTraceExtractor.windowCenterFromMin(r.getMinMz()),
              Double.max(0.00, r.getPeakTime()))).
          forEach(p -> writeRow(writer, p));
      timeMax = analysisResults.stream().map(WindowAnalysisResult::getPeakTime).max(Double::compare);
      writer.write("\n\n");
    }

    Gnuplotter plotter = new Gnuplotter();
    List<Gnuplotter.PlotConfiguration> plotConfigurations = new ArrayList<Gnuplotter.PlotConfiguration>() {{
      // There will definitely be values present for every Optional max.
      add(new Gnuplotter.PlotConfiguration(Gnuplotter.PlotConfiguration.KIND.GRAPH,
          "LogSNR", 0, snrMax.get()));
      add(new Gnuplotter.PlotConfiguration(Gnuplotter.PlotConfiguration.KIND.GRAPH,
          "Max Intensity", 1, intensityMax.get()));
      add(new Gnuplotter.PlotConfiguration(Gnuplotter.PlotConfiguration.KIND.GRAPH,
          "timeMax", 2, timeMax.get()));
    }};
    plotter.plot2D(dataFile.getAbsolutePath(), outputFile.getAbsolutePath(), "M/Z", "LogSNR, Intensity, or Time",
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
