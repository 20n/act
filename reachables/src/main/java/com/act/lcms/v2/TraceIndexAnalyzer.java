package com.act.lcms.v2;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TraceIndexAnalyzer {
  private static final Logger LOGGER = LogManager.getFormatterLogger(TraceIndexAnalyzer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Integer WAVEFORM_TIME_SLICE_WINDOW = 5; // Same as WaveformAnalysis.
  private static final Double WAVEFORM_MIN_THRESHOLD = 250.0d; // Same as WaveformAnalysis, but what units?!

  private static final String OPTION_INDEX_PATH = "x";
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
        new File(cl.getOptionValue(OPTION_OUTPUT_PATH))
    );

    LOGGER.info("Done");
  }

  private void runExtraction(File rocksDBFile, File outputFile)
      throws RocksDBException, IOException {
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

      Pair<List<XZ>, Map<Double, Double>> timeWindowsAndMaxes =
          WaveformAnalysis.compressIntensityAndTimeGraphsAndFindMaxIntensityInEveryTimeWindow(
              targetAndTrace.getRight(), WAVEFORM_TIME_SLICE_WINDOW); // Same as waveform analysis
      List<XZ> calledPeaks = WaveformAnalysis.detectPeaksInIntensityTimeWaveform(
          timeWindowsAndMaxes.getLeft(), WAVEFORM_MIN_THRESHOLD); // Same as waveform analysis.

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
  }
}
