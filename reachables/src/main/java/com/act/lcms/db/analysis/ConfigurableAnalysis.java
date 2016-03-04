package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.io.parser.TSVParser;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigurableAnalysis {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_CONFIG_FILE = "c";
  public static final String OPTION_USE_HEATMAP = "e";
  public static final String OPTION_FONT_SCALE = "s";
  public static final String OPTION_USE_SNR = "r";

  /* TODO: this format was based on a TSV sample Chris produced.  We should improve it to better match the format of the
   * data that is available in the DB. */
  public static final String HEADER_SAMPLE = "trigger/sample";
  public static final String HEADER_LABEL = "label";
  public static final String HEADER_EXACT_MASS = "exact_mass";
  public static final String HEADER_PRECISION = "precision";
  public static final String HEADER_INTENSITY_RANGE_GROUP = "normalization_group";
  public static final String[] EXPECTED_HEADER_FIELDS = new String[]{
      HEADER_SAMPLE, HEADER_LABEL, HEADER_EXACT_MASS, HEADER_PRECISION, HEADER_INTENSITY_RANGE_GROUP
  };

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "LCMS analysis class that takes as its input a configuration TSV and outputs plots ",
      "of the plates/masses specified in that configuration file. The expected rows in the ",
      "configuration file are based on a mockup Chris created, and look like:\n",
      "`<plate barcode>|<well coordinates>\\t<label>\\t<mass or chemical>\\t",
      "<resolution (only 0.01 or 0.001)>\\t<y axis range group>`\n\n",
      "Expected TSV fields are:\n",
      StringUtils.join(EXPECTED_HEADER_FIELDS, ", "), "\n\n"
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DIRECTORY)
            .argName("directory")
            .desc("The directory where LCMS analysis results live")
            .hasArg().required()
            .longOpt("data-dir")
    );
    add(Option.builder(OPTION_OUTPUT_PREFIX)
            .argName("output prefix")
            .desc("A prefix for the output data/pdf files")
            .hasArg().required()
            .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_CONFIG_FILE)
            .argName("configuration file")
            .desc("A configuration TSV that determines the layout of the analysis")
            .hasArg().required()
            .longOpt("config-file")
    );
    add(Option.builder(OPTION_USE_SNR)
        .desc("Use signal-to-noise ratio instead of max intensity for peak identification")
        .longOpt("use-snr")
    );

    add(Option.builder(OPTION_USE_HEATMAP)
            .desc("Produce a heat map rather than a 2d line plot")
            .longOpt("heat-map")
    );

    add(Option.builder(OPTION_FONT_SCALE)
            .argName("font scale")
            .desc("A Gnuplot fontscale value, should be between 0.1 and 0.5 (0.4 works if the graph text is large")
            .hasArg()
            .longOpt("font-scale")
    );
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public static class AnalysisStep {
    public enum KIND {
      HEADER,
      SEPARATOR_LARGE,
      SEPARATOR_SMALL,
      SAMPLE,
    }

    private Integer index;
    private KIND kind;
    private String plateBarcode;
    private String plateCoords;
    private String label;
    private Double exactMass;
    private Boolean useFineGrainedMZTolerance;
    private Integer intensityRangeGroup;

    public AnalysisStep(Integer index, KIND kind, String plateBarcode, String plateCoords, String label,
                        Double exactMass, Boolean useFineGrainedMZTolerance, Integer intensityRangeGroup) {
      this.index = index;
      this.kind = kind;
      this.plateBarcode = plateBarcode;
      this.plateCoords = plateCoords;
      this.label = label;
      this.exactMass = exactMass;
      this.useFineGrainedMZTolerance = useFineGrainedMZTolerance;
      this.intensityRangeGroup = intensityRangeGroup;
    }

    public Integer getIndex() {
      return index;
    }

    public KIND getKind() {
      return kind;
    }

    public String getPlateBarcode() {
      return plateBarcode;
    }

    public String getPlateCoords() {
      return plateCoords;
    }

    public String getLabel() {
      return label;
    }

    public Double getExactMass() {
      return exactMass;
    }

    public Boolean getUseFineGrainedMZTolerance() {
      return useFineGrainedMZTolerance;
    }

    public Integer getIntensityRangeGroup() {
      return intensityRangeGroup;
    }
  }

  public static final Pattern SAMPLE_WELL_PATTERN = Pattern.compile("^(.*)\\|([A-Za-z]+[0-9])+$");

  public static final String EXPECTED_LOW_PRECISION_STRING = "0.01";
  public static final String EXPECTED_HIGH_PRECISION_STRING = "0.001";

  public static AnalysisStep mapToStep(DB db, Integer index, Map<String, String> map) throws SQLException {
    String sample = map.get(HEADER_SAMPLE);
    if (sample == null || sample.isEmpty()) {
      System.err.format("Missing required field %s in line %d, skipping\n", HEADER_SAMPLE, index);
      return null;
    }
    if (sample.startsWith("<header")) {
      return new AnalysisStep(index, AnalysisStep.KIND.HEADER, null, null, map.get(HEADER_LABEL), null, null, null);
    }
    if (sample.equals("<large_line_separator>")) {
      return new AnalysisStep(index, AnalysisStep.KIND.SEPARATOR_LARGE, null, null, null, null, null, null);
    }
    if (sample.equals("<small_line_separator>")) {
      return new AnalysisStep(index, AnalysisStep.KIND.SEPARATOR_SMALL, null, null, null, null, null, null);
    }

    Matcher sampleMatcher = SAMPLE_WELL_PATTERN.matcher(sample);
    if (!sampleMatcher.matches()) {
      System.err.format("Unable to interpret sample field %s in line %d, skipping\n", sample, index);
      return null;
    }
    String plateBarcode = sampleMatcher.group(1);
    String plateCoords = sampleMatcher.group(2);

    String label = map.get(HEADER_LABEL);
    Pair<String, Double> exactMass = Utils.extractMassFromString(db, map.get(HEADER_EXACT_MASS));

    Boolean useFineGrainedMZTolerance = false;
    String precisionString = map.get(HEADER_PRECISION);
    if (precisionString != null && !precisionString.isEmpty()) {
      switch (map.get(HEADER_PRECISION)) {
        case EXPECTED_LOW_PRECISION_STRING:
          useFineGrainedMZTolerance = false;
          break;
        case EXPECTED_HIGH_PRECISION_STRING:
          useFineGrainedMZTolerance = true;
          break;
        case "low":
          useFineGrainedMZTolerance = false;
          break;
        case "high":
          useFineGrainedMZTolerance = true;
          break;
        case "coarse":
          useFineGrainedMZTolerance = false;
          break;
        case "fine":
          useFineGrainedMZTolerance = true;
          break;
        default:
          System.err.format("Invalid precision value %s, defaulting to coarse-grained analysis\n", precisionString);
          useFineGrainedMZTolerance = false;
          break;
      }
    }

    String intensityGroupString = map.get(HEADER_INTENSITY_RANGE_GROUP);
    Integer intensityGroup = -1;
    if (intensityGroupString != null && !intensityGroupString.isEmpty()) {
      intensityGroup = Integer.parseInt(intensityGroupString);
    }

    return new AnalysisStep(index, AnalysisStep.KIND.SAMPLE, plateBarcode, plateCoords, label, exactMass.getRight(),
        useFineGrainedMZTolerance, intensityGroup);
  }

  // TODO: do we want to make this configurable?  Or should we always just search for M?
  public static final Set<String> SEARCH_IONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("M+H")));
  public static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>(0));
  public static void runAnalysis(DB db, File lcmsDir, String outputPrefix, List<AnalysisStep> steps,
                                 boolean makeHeatmaps, Double fontScale, boolean useSNR)
      throws SQLException, Exception {
    HashMap<String, Plate> platesByBarcode = new HashMap<>();
    HashMap<Integer, Plate> platesById = new HashMap<>();
    Map<Integer, Pair<List<ScanData<LCMSWell>>, Double>> lcmsResults = new HashMap<>();
    Map<Integer, Pair<List<ScanData<StandardWell>>, Double>> standardResults = new HashMap<>();
    Map<Integer, Double> intensityGroupMaximums = new HashMap<>();
    for (AnalysisStep step : steps) {
      // There's no trace analysis to perform for non-sample steps.
      if (step.getKind() != AnalysisStep.KIND.SAMPLE) {
        continue;
      }

      Plate p = platesByBarcode.get(step.getPlateBarcode());
      if (p == null) {
        p = Plate.getPlateByBarcode(db, step.getPlateBarcode());
        if (p == null) {
          throw new IllegalArgumentException(String.format("Found invalid plate barcode '%s' for analysis component %d",
              step.getPlateBarcode(), step.getIndex()));
        }
        platesByBarcode.put(p.getBarcode(), p);
        platesById.put(p.getId(), p);
      }

      Pair<Integer, Integer> coords = Utils.parsePlateCoordinates(step.getPlateCoords());
      List<Pair<String, Double>> searchMZs =
          Collections.singletonList(Pair.of("Configured m/z value", step.getExactMass()));
      Double maxIntesnsity = null;
      switch (p.getContentType()) {
        case LCMS:
          // We don't know which of the scans are positive samples and which are negatives, so call them all positive.
          List<LCMSWell> lcmsSamples = Collections.singletonList(
              LCMSWell.getInstance().getByPlateIdAndCoordinates(db, p.getId(), coords.getLeft(), coords.getRight()));
          Pair<List<ScanData<LCMSWell>>, Double> lcmsScanData =
              AnalysisHelper.processScans(db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, platesById, lcmsSamples,
                  step.getUseFineGrainedMZTolerance(), SEARCH_IONS, EMPTY_SET, useSNR);
          lcmsResults.put(step.getIndex(), lcmsScanData);
          maxIntesnsity = lcmsScanData.getRight();
          break;
        case STANDARD:
          List<StandardWell> standardSamples = Collections.singletonList(
              StandardWell.getInstance().getStandardWellsByPlateIdAndCoordinates(db, p.getId(), coords.getLeft(), coords.getRight()));
          Pair<List<ScanData<StandardWell>>, Double> standardScanData =
              AnalysisHelper.processScans(db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, platesById, standardSamples,
                  step.getUseFineGrainedMZTolerance(), SEARCH_IONS, EMPTY_SET, useSNR);
          standardResults.put(step.getIndex(), standardScanData);
          maxIntesnsity = standardScanData.getRight();
          break;
        default:
          throw new IllegalArgumentException(
              String.format("Invalid plate content kind %s for plate %s in analysis component %d",
              p.getContentType(), p.getBarcode(), step.getIndex()));
      }
      Double existingMax = intensityGroupMaximums.get(step.getIntensityRangeGroup());
      if (existingMax == null || existingMax < maxIntesnsity) { // TODO: is this the right max intensity?
        intensityGroupMaximums.put(step.getIntensityRangeGroup(), maxIntesnsity);
      }
    }

    // Prep the chart labels/types, write out the data, and plot the charts.
    File dataFile = new File(outputPrefix + ".data");
    List<Gnuplotter.PlotConfiguration> plotConfigurations = new ArrayList<>(steps.size());
    int numGraphs = 0;
    try (FileOutputStream fos = new FileOutputStream(dataFile)) {
      for (AnalysisStep step : steps) {
        if (step.getKind() == AnalysisStep.KIND.HEADER) {
          // TODO: change the Gnuplotter API to add headings and update this.
          plotConfigurations.add(new Gnuplotter.PlotConfiguration(
              Gnuplotter.PlotConfiguration.KIND.HEADER,
              step.getLabel(),
              null,
              null
          ));
          continue;
        }
        if (step.getKind() == AnalysisStep.KIND.SEPARATOR_LARGE ||
            step.getKind() == AnalysisStep.KIND.SEPARATOR_SMALL) {
          // TODO: change the Gnuplotter API to add headings and update this.
          plotConfigurations.add(new Gnuplotter.PlotConfiguration(
              Gnuplotter.PlotConfiguration.KIND.SEPARATOR,
              "",
              null,
              null
          ));
          continue;
        }
        Plate p = platesByBarcode.get(step.getPlateBarcode());
        Double maxIntensity = intensityGroupMaximums.get(step.getIntensityRangeGroup());
        switch (p.getContentType()) {
          case LCMS:
            Pair<List<ScanData<LCMSWell>>, Double> lcmsPair = lcmsResults.get(step.getIndex());
            if (lcmsPair.getLeft().size() > 1) {
              System.err.format("Found multiple scan files for LCMW well %s @ %s, using first\n",
                  step.getPlateBarcode(), step.getPlateCoords());
            }
            AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity,
                lcmsPair.getLeft().get(0), makeHeatmaps, false);
            break;
          case STANDARD:
            Pair<List<ScanData<StandardWell>>, Double> stdPair = standardResults.get(step.getIndex());
            if (stdPair.getLeft().size() > 1) {
              System.err.format("Found multiple scan files for standard well %s @ %s, using first\n",
                  step.getPlateBarcode(), step.getPlateCoords());
            }
            AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity,
                stdPair.getLeft().get(0), makeHeatmaps, false);
            break;
          default:
            // This case represents a bug, so it's a RuntimeException.
            throw new RuntimeException(String.format("Found unexpected content type %s for plate %s on analysis step %d",
                p.getContentType(), p.getBarcode(), step.getIndex()));
        }
        plotConfigurations.add(new Gnuplotter.PlotConfiguration(
            Gnuplotter.PlotConfiguration.KIND.GRAPH,
            step.getLabel(),
            numGraphs,
            maxIntensity
        ));
        numGraphs++;
      }

      String fmt = "pdf";
      File imgFile = new File(outputPrefix + "." + fmt);
      Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
      if (makeHeatmaps) {
        plotter.plotHeatmap(dataFile.getAbsolutePath(), imgFile.getAbsolutePath(),
            fmt, null, null, plotConfigurations, imgFile + ".gnuplot");
      } else {
        plotter.plot2D(dataFile.getAbsolutePath(), imgFile.getAbsolutePath(),
            "time", "intensity", fmt, null, null, plotConfigurations, imgFile + ".gnuplot");
      }
    }
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

    File lcmsDir = new File(cl.getOptionValue(OPTION_DIRECTORY));
    if (!lcmsDir.isDirectory()) {
      System.err.format("File at %s is not a directory\n", lcmsDir.getAbsolutePath());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    Double fontScale = null;
    if (cl.hasOption("font-scale")) {
      try {
        fontScale = Double.parseDouble(cl.getOptionValue("font-scale"));
      } catch (IllegalArgumentException e) {
        System.err.format("Argument for font-scale must be a floating point number.\n");
        System.exit(1);
      }
    }

    File configFile = new File(cl.getOptionValue(OPTION_CONFIG_FILE));
    if (!configFile.isFile()) {
      throw new IllegalArgumentException(String.format("Not a regular file at %s", configFile.getAbsolutePath()));
    }
    TSVParser parser = new TSVParser();
    parser.parse(configFile);

    try (DB db = DB.openDBFromCLI(cl)) {
      System.out.format("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      List<AnalysisStep> steps = new ArrayList<>(parser.getResults().size());
      int i = 0;
      for (Map<String, String> row : parser.getResults()) {
        AnalysisStep step = mapToStep(db, i, row);
        if (step != null) {
          System.out.format("%d: %s '%s' %s %s %f %s\n", step.getIndex(), step.getKind(), step.getLabel(),
              step.getPlateBarcode(), step.getPlateCoords(), step.getExactMass(), step.getUseFineGrainedMZTolerance());
        }
        steps.add(step);
        i++;
      }

      System.out.format("Running analysis\n");
      runAnalysis(db, lcmsDir, cl.getOptionValue(OPTION_OUTPUT_PREFIX),
          steps, cl.hasOption(OPTION_USE_HEATMAP), fontScale, cl.hasOption(OPTION_USE_SNR));
    }
  }
}
