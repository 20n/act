package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.CuratedChemical;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IonSearchAnalysis {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_STRAINS = "s";
  public static final String OPTION_CONSTRUCTS = "c";
  public static final String OPTION_NEGATIVE_STRAINS = "S";
  public static final String OPTION_NEGATIVE_CONSTRUCTS = "C";
  public static final String OPTION_STANDARD_NAME = "sn";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_STANDARD_WELLS = "sw";
  public static final String OPTION_SEARCH_MZ = "m";
  public static final String OPTION_NO_STANDARD = "ns";
  public static final String OPTION_FILTER_BY_PLATE_BARCODE = "p";
  public static final String OPTION_USE_HEATMAP = "e";
  public static final String OPTION_USE_SNR = "r";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class applies the MS1 LCMS analysis to a combination of ",
      "standards and samples.  Specify positive constructs/strains and negative ",
      "controls to be analyzed and graphed together.\nStandards will be determined by ",
      "the positive samples' targets if a standard is not explicitly specified.\n",
      "An m/z value or chemical for which to search in the LCMS trace data can be ",
      "explicitly specified; if no search chemical is specified and all positive samples ",
      "share a single target, that target's mass will be used."
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
            .desc("A prefix for the output data/pdf lcms")
            .hasArg().required()
            .longOpt("output-prefix")
    );

    add(Option.builder(OPTION_STRAINS)
            .argName("strains")
            .desc("The msids of the strains to be analyzed (specify only one of 'strain' or 'construct')")
            .hasArgs().valueSeparator(',')
            .longOpt("msids")
    );
    add(Option.builder(OPTION_CONSTRUCTS)
            .argName("constructs")
            .desc("The construct ids (composition) to be analyzed (specify only one of 'strain' or 'construct')")
            .hasArgs().valueSeparator(',')
            .longOpt("construct-ids")
    );
    add(Option.builder(OPTION_NEGATIVE_STRAINS)
            .argName("negative-strains")
            .desc("A strains to use as a negative control (the first novel LCMS sample will be used)")
            .hasArgs().valueSeparator(',')
            .longOpt("negative-msids")
    );
    add(Option.builder(OPTION_NEGATIVE_CONSTRUCTS)
            .argName("constructs")
            .desc("A constructs to use as a negative control (the first novel LCMS sample will be used)")
            .hasArgs().valueSeparator(',')
            .longOpt("negative-construct-ids")
    );
    add(Option.builder(OPTION_STANDARD_NAME)
            .argName("standard's chemical name")
            .desc("The name of the chemical to use as a standard (default will be LCMS wells' target)")
            .hasArg()
            .longOpt("standard-name")
    );
    add(Option.builder(OPTION_STANDARD_PLATE_BARCODE)
            .argName("standard plate barcode")
            .desc("The plate barcode to use when searching for a compatible standard")
            .hasArg().required()
            .longOpt("standard-plate")
    );
    add(Option.builder(OPTION_SEARCH_MZ)
            .argName("search chem")
            .desc("The m/z or chemical name to search for (default will use target of LCMS wells)")
            .hasArg()
            .longOpt("search-chem")
    );
    add(Option.builder(OPTION_NO_STANDARD)
            .argName("no standard")
            .desc("Specifies that the analysis should be completed without a standard")
            .longOpt("no-standard")
    );
    add(Option.builder(OPTION_STANDARD_WELLS)
            .argName("well coordinates")
            .desc("Specifies a specific well or wells in the standard plate to use as the standard sample")
            .hasArgs().valueSeparator(',')
            .longOpt("standard-wells")
    );
    add(Option.builder()
            .argName("ion list")
            .desc("A comma-separated list of ions to include in the search (ions not in this list will be ignored)")
            .hasArgs().valueSeparator(',')
            .longOpt("include-ions")
    );
    add(Option.builder()
            .argName("ion list")
            .desc("A comma-separated list of ions to exclude from the search, takes precedence over include-ions")
            .hasArgs().valueSeparator(',')
            .longOpt("exclude-ions")
    );
    add(Option.builder(OPTION_FILTER_BY_PLATE_BARCODE)
            .argName("plate barcode list")
            .desc("A list of plate barcodes to consider, all other plates will be ignored")
            .hasArgs().valueSeparator(',')
            .longOpt("include-plates")
    );
    add(Option.builder(OPTION_USE_HEATMAP)
            .desc("Produce a heat map rather than a 2d line plot")
            .longOpt("heat-map")
    );
    // TODO: add filter on SCAN_MODE.
    add(Option.builder(OPTION_USE_SNR)
            .desc("Use signal-to-noise ratio instead of max intensity for peak identification")
            .longOpt("use-snr")
    );

    add(Option.builder()
            .argName("font scale")
            .desc("A Gnuplot fontscale value, should be between 0.1 and 0.5 (0.4 works if the graph text is large")
            .hasArg()
            .longOpt("font-scale")
    );
    add(Option.builder()
            .desc(String.format(
                "Use fine-grained M/Z tolerance (%.3f) when conducting the MS1 analysis " +
                    "instead of default M/Z tolerance %.3f",
                MS1.MS1_MZ_TOLERANCE_FINE, MS1.MS1_MZ_TOLERANCE_DEFAULT))
            .longOpt("fine-grained-mz")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
    );
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
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

    try (DB db = DB.openDBFromCLI(cl)) {
      Set<String> includeIons = null;
      if (cl.hasOption("include-ions")) {
        String[] ionNames = cl.getOptionValues("include-ions");
        includeIons = new HashSet<>(Arrays.asList(ionNames));
        System.out.format("Including ions in search: %s\n", StringUtils.join(includeIons, ", "));
      }
      Set<String> excludeIons = null;
      if (cl.hasOption("exclude-ions")) {
        String[] ionNames = cl.getOptionValues("exclude-ions");
        excludeIons = new HashSet<>(Arrays.asList(ionNames));
        System.out.format("Excluding ions from search: %s\n", StringUtils.join(excludeIons, ", "));
      }

      Set<Integer> takeSamplesFromPlateIds = null;
      if (cl.hasOption(OPTION_FILTER_BY_PLATE_BARCODE)) {
        String[] plateBarcodes = cl.getOptionValues(OPTION_FILTER_BY_PLATE_BARCODE);
        System.out.format("Considering only sample wells in plates: %s\n", StringUtils.join(plateBarcodes, ", "));
        takeSamplesFromPlateIds = new HashSet<>(plateBarcodes.length);
        for (String plateBarcode : plateBarcodes) {
          Plate p = Plate.getPlateByBarcode(db, plateBarcode);
          if (p == null) {
            System.err.format("WARNING: unable to find plate in DB with barcode %s\n", plateBarcode);
          } else {
            takeSamplesFromPlateIds.add(p.getId());
          }
        }
        // Allow filtering on barcode even if we couldn't find any in the DB.
      }

      System.out.format("Loading/updating LCMS scan lcms into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      System.out.format("Processing LCMS scans\n");
      Pair<List<LCMSWell>, Set<Integer>> positiveWellsAndPlateIds = Utils.extractWellsAndPlateIds(
          db, cl.getOptionValues(OPTION_STRAINS), cl.getOptionValues(OPTION_CONSTRUCTS),
          takeSamplesFromPlateIds, false);
      List<LCMSWell> positiveWells = positiveWellsAndPlateIds.getLeft();
      if (positiveWells.size() == 0) {
        throw new RuntimeException("Found no LCMS wells for specified strains/constructs");
      }
      // Only take negative samples from the plates where we found the positive samples.
      Pair<List<LCMSWell>, Set<Integer>> negativeWellsAndPlateIds =
          Utils.extractWellsAndPlateIds(
              db, cl.getOptionValues(OPTION_NEGATIVE_STRAINS), cl.getOptionValues(OPTION_NEGATIVE_CONSTRUCTS),
              positiveWellsAndPlateIds.getRight(), true);
      List<LCMSWell> negativeWells = negativeWellsAndPlateIds.getLeft();
      if (negativeWells == null || negativeWells.size() == 0) {
        System.err.format("WARNING: no valid negative samples found in same plates as positive samples\n");
      }

      // Extract the reference MZ that will be used in the LCMS trace processing.
      List<Pair<String, Double>> searchMZs = null;
      Set<CuratedChemical> standardChemicals = null;
      List<ChemicalAssociatedWithPathway> pathwayChems = null;
      if (cl.hasOption(OPTION_SEARCH_MZ)) {
        // Assume mz can be an FP number of a chemical name.
        String massStr = cl.getOptionValue(OPTION_SEARCH_MZ);
        Pair<String, Double> searchMZ = Utils.extractMassFromString(db, massStr);
        if (searchMZ != null) {
          searchMZs = Collections.singletonList(searchMZ);
        }
        standardChemicals = Utils.extractTargetsForWells(db, positiveWells);
      } else {
        CuratedChemical targetChemical = Utils.requireOneTarget(db, positiveWells);
        if (targetChemical == null) {
          throw new RuntimeException(
              "Unable to find a curated chemical entry for specified strains'/constructs' targets.  " +
                  "Please specify a chemical name or m/z explicitly or update the curated chemicals list in the DB.");
        }
        System.out.format("Using reference M/Z for positive target %s (%f)\n",
            targetChemical.getName(), targetChemical.getMass());
        searchMZs = Collections.singletonList(Pair.of(targetChemical.getName(), targetChemical.getMass()));
        standardChemicals = Collections.singleton(targetChemical);
      }

      // Look up the standard by name, or use the target if none is specified.
      List<StandardWell> standardWells = null;
      if (cl.hasOption(OPTION_NO_STANDARD)) {
        System.err.format("WARNING: skipping standard comparison (no-standard option specified)\n");
        standardWells = new ArrayList<>(0);
      } else if (cl.hasOption(OPTION_STANDARD_WELLS)) {
        String[] standardCoordinates = cl.getOptionValues(OPTION_STANDARD_WELLS);
        standardWells = new ArrayList<>(standardCoordinates.length);
        Plate standardPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
        List<String> foundCoordinates = new ArrayList<>(standardCoordinates.length);
        for (String stringCoords : standardCoordinates) {
          Pair<Integer, Integer> coords = Utils.parsePlateCoordinates(stringCoords);
          StandardWell well = StandardWell.getInstance().getStandardWellsByPlateIdAndCoordinates(
              db, standardPlate.getId(), coords.getLeft(), coords.getRight());
          if (well == null) {
            System.err.format("Unable to find standard well at %s [%s]\n", standardPlate.getBarcode(), stringCoords);
            continue;
          }
          standardWells.add(well);
          foundCoordinates.add(stringCoords);
        }
        System.out.format("Using explicitly specified standard wells %s [%s]\n", standardPlate.getBarcode(),
            StringUtils.join(foundCoordinates, ", "));
      } else if (cl.hasOption(OPTION_STANDARD_NAME)) {
        String standardName = cl.getOptionValue(OPTION_STANDARD_NAME);
        System.out.format("Using explicitly specified standard %s\n", standardName);
        standardWells = Collections.singletonList(
            Utils.extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName));
      } else if (standardChemicals != null && standardChemicals.size() > 0) {
        // Default to using the target chemical(s) as a standard if none is specified.
        standardWells = new ArrayList<>(standardChemicals.size());
        for (CuratedChemical c : standardChemicals) {
          String standardName = c.getName();
          System.out.format("Searching for well containing standard %s\n", standardName);
          standardWells.add(
              Utils.extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName));
        }
      }

      boolean useFineGrainedMZ = cl.hasOption("fine-grained-mz");
      boolean useSNR = cl.hasOption(OPTION_USE_SNR);

      /* Process the standard, positive, and negative wells, producing ScanData containers that will allow them to be
       * iterated over for graph writing. */
      HashMap<Integer, Plate> plateCache = new HashMap<>();
      Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, standardWells,
              useFineGrainedMZ, includeIons, excludeIons, useSNR);
      Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, plateCache, positiveWells,
              useFineGrainedMZ, includeIons, excludeIons, useSNR);
      Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.NEG_CONTROL, plateCache, negativeWells,
              useFineGrainedMZ, includeIons, excludeIons, useSNR);

      String fmt = "pdf";
      String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + fmt;
      String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + ".data";
      System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

      produceLCMSSearchPlots(lcmsDir, outData, outImg, allStandardScans, allPositiveScans,
          allNegativeScans, fontScale, useFineGrainedMZ, cl.hasOption(OPTION_USE_HEATMAP), useSNR);
    }
  }

  public static void produceLCMSSearchPlots(File lcmsDir, String outData, String outImg,
                                            Pair<List<ScanData<StandardWell>>, Double> allStandardScans,
                                            Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans,
                                            Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans,
                                            Double fontScale, boolean useFineGrainedMZ, boolean makeHeatmaps,
                                            boolean useSNR)
      throws Exception {
    List<ScanData> allScanData = new ArrayList<ScanData>() {{
      addAll(allStandardScans.getLeft());
      addAll(allPositiveScans.getLeft());
      addAll(allNegativeScans.getLeft());
    }};
    // Get the global maximum intensity across all scans.
    Double maxIntensity = Math.max(allStandardScans.getRight(),
        Math.max(allPositiveScans.getRight(), allNegativeScans.getRight()));
    System.out.format("Processing LCMS scans for graphing:\n");
    for (ScanData scanData : allScanData) {
      System.out.format("  %s\n", scanData.toString());
    }

    String fmt = "pdf";
    System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

    // Generate the data file and graphs.
    try (FileOutputStream fos = new FileOutputStream(outData)) {
      // Write all the scan data out to a single data file.
      List<String> graphLabels = new ArrayList<>();
      for (ScanData scanData : allScanData) {
        graphLabels.addAll(AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, scanData, makeHeatmaps, true));
      }

      Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
      if (makeHeatmaps) {
        plotter.plotHeatmap(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), maxIntensity, fmt);
      } else {
        plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time",
            maxIntensity, "intensity", fmt);
      }
    }
  }
}
