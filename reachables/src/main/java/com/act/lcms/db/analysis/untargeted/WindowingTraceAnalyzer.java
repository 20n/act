package com.act.lcms.db.analysis.untargeted;

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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class WindowingTraceAnalyzer {
  private static final Logger LOGGER = LogManager.getFormatterLogger(WindowingTraceAnalyzer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


  private static final String OPTION_INDEX_PATH = "x";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class consumes windowed traces from an LCMS scan files, searching each window for peaks."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index is be stored; mustalready exist")
        .hasArg().required()
        .longOpt("index")
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

      String json = OBJECT_MAPPER.writeValueAsString(result);
      LOGGER.info(json);

    }
  }
}
