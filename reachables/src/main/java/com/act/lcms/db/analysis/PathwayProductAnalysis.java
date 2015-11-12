package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathwayProductAnalysis {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_STRAINS = "s";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_NEGATIVE_STRAINS = "S";
  public static final String OPTION_NEGATIVE_CONSTRUCTS = "C";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_FILTER_BY_PLATE_BARCODE = "p";
  public static final String OPTION_USE_HEATMAP = "e";
  public static final String OPTION_SEARCH_ION = "i";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class applies the MS1 LCMS analysis to a combination of ",
      "standards and samples for the specified ion of all intermediate, side-reaction, ",
      "and final products of a given construct (optionally filtering samples by strains).  ",
      "An appropriate standard and any specified negative controls will be plotted alongside ",
      "a sample analysis for each product.",
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
    add(Option.builder(OPTION_CONSTRUCT)
            .argName("construct id")
            .desc("A construct whose intermediate/side-reaction products should be searched for in the traces")
            .hasArg()
            .longOpt("construct-id")
    );
    add(Option.builder(OPTION_STRAINS)
            .argName("strains")
            .desc("Filter analyzed LCMS samples to only these strains")
            .hasArgs().valueSeparator(',')
            .longOpt("msids")
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
    add(Option.builder(OPTION_STANDARD_PLATE_BARCODE)
            .argName("standard plate barcode")
            .desc("The plate barcode to use when searching for a compatible standard")
            .hasArg().required()
            .longOpt("standard-plate")
    );
    add(Option.builder(OPTION_SEARCH_ION)
            .argName("search ion")
            .desc("The ion for which to search (default is M+H)")
            .hasArg()
            .longOpt("search-ion")
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

      System.out.format("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      System.out.format("Processing LCMS scans\n");
      Pair<List<LCMSWell>, Set<Integer>> positiveWellsAndPlateIds = Utils.extractWellsAndPlateIds(
          db, cl.getOptionValues(OPTION_STRAINS), cl.getOptionValues(OPTION_CONSTRUCT), takeSamplesFromPlateIds, false);
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

      // Extract the chemicals in the pathway and their product masses, then look up info on those chemicals
      List<Pair<ChemicalAssociatedWithPathway, Double>> productMasses =
          Utils.extractMassesForChemicalsAssociatedWithConstruct(db, cl.getOptionValue(OPTION_CONSTRUCT));
      List<Pair<String, Double>> searchMZs = new ArrayList<>(productMasses.size());
      List<ChemicalAssociatedWithPathway> pathwayChems = new ArrayList<>(productMasses.size());
      for (Pair<ChemicalAssociatedWithPathway, Double> productMass : productMasses) {
        String chemName = productMass.getLeft().getChemical();
        searchMZs.add(Pair.of(chemName, productMass.getRight()));
        pathwayChems.add(productMass.getLeft());
      }
      System.out.format("Searching for intermediate/side-reaction products:\n");
      for (Pair<String, Double> searchMZ : searchMZs) {
        System.out.format("  %s: %.3f\n", searchMZ.getLeft(), searchMZ.getRight());
      }

      // Look up the standard by name.
      List<StandardWell> standardWells = new ArrayList<>(pathwayChems.size());
      for (ChemicalAssociatedWithPathway c : pathwayChems) {
        String standardName = c.getChemical();
        System.out.format("Searching for well containing standard %s\n", standardName);
        standardWells.add(
            Utils.extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName));
      }

      boolean useFineGrainedMZ = cl.hasOption("fine-grained-mz");

      String searchIon = "M+H";
      if (cl.hasOption(OPTION_SEARCH_ION)) {
        searchIon = cl.getOptionValue(OPTION_SEARCH_ION);
      }
      Set<String> includeIons = Collections.singleton(searchIon);

      /* Process the standard, positive, and negative wells, producing ScanData containers that will allow them to be
       * iterated over for graph writing. */
      HashMap<Integer, Plate> plateCache = new HashMap<>();
      Set<String> emptySet = new HashSet<>(0);
      Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, standardWells,
              useFineGrainedMZ, includeIons, emptySet);
      Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, plateCache, positiveWells,
              useFineGrainedMZ, includeIons, emptySet);
      Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.NEG_CONTROL, plateCache, negativeWells,
              useFineGrainedMZ, includeIons, emptySet);


      String fmt = "pdf";
      String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + fmt;
      String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + ".data";
      System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

      produceLCMSPathwayHeatmaps(lcmsDir, outData, outImg, pathwayChems, allStandardScans, allPositiveScans,
          allNegativeScans, fontScale, useFineGrainedMZ, cl.hasOption(OPTION_USE_HEATMAP), ScanFile.SCAN_MODE.POS);
    }
  }

  private static final Comparator<ScanData<LCMSWell>> LCMS_SCAN_COMPARATOR =
      new Comparator<ScanData<LCMSWell>>() {
        @Override
        public int compare(ScanData<LCMSWell> o1, ScanData<LCMSWell> o2) {
          int c;
          // TODO: consider feeding conditions in sort to match condition order to steps.
          c = o1.getWell().getMsid().compareTo(o2.getWell().getMsid());
          if (c != 0) return c;
          c = o1.getPlate().getBarcode().compareTo(o2.getPlate().getBarcode());
          if (c != 0) return c;
          c = o1.getWell().getPlateRow().compareTo(o2.getWell().getPlateRow());
          if (c != 0) return c;
          c = o1.getWell().getPlateColumn().compareTo(o2.getWell().getPlateColumn());
          if (c != 0) return c;
          c = o1.getScanFile().getFilename().compareTo(o2.getScanFile().getFilename());
          return c;
        }
      };

  private static final ScanData<LCMSWell> BLANK_SCAN =
      new ScanData<>(ScanData.KIND.BLANK, null, null, null, null, null, null);

  public static void produceLCMSPathwayHeatmaps(File lcmsDir, String outData, String outImg,
                                                List<ChemicalAssociatedWithPathway> pathwayChems,
                                                Pair<List<ScanData<StandardWell>>, Double> allStandardScans,
                                                Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans,
                                                Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans,
                                                Double fontScale, boolean useFineGrainedMZ, boolean makeHeatmaps,
                                                ScanFile.SCAN_MODE scanMode) throws Exception {
    Map<String, Integer> chemToIndex = new HashMap<>();
    for (ChemicalAssociatedWithPathway chem : pathwayChems) {
      chemToIndex.put(chem.getChemical(), chem.getIndex());
    }

    String fmt = "pdf";
    System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

    Double globalMaxIntensity = 0.0d;

    // Generate the data file and graphs.
    try (FileOutputStream fos = new FileOutputStream(outData)) {
      List<String> graphLabels = new ArrayList<>();
      List<Double> yMaxList = new ArrayList<>();
      for (ChemicalAssociatedWithPathway chem : pathwayChems) {
        System.out.format("Processing data for pathway chemical %s\n", chem.getChemical());

        Double maxIntensity = 0.0d;

        // Extract the first available
        ScanData<StandardWell> stdScan = null;
        for (ScanData<StandardWell> scan : allStandardScans.getLeft()) {
          if (chem.getChemical().equals(scan.getWell().getChemical()) &&
              chem.getChemical().equals(scan.getTargetChemicalName())) {
            if (scanMode == null || scanMode.equals(scan.getScanFile().getMode())) {
              stdScan = scan;
              maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxYAxis());
              break;
            }
          }
        }
        if (stdScan == null) {
          System.err.format("WARNING: unable to find standard well scan for chemical %s\b", chem.getChemical());
        }

        List<ScanData<LCMSWell>> matchinPosScans = new ArrayList<>();
        for (ScanData<LCMSWell> scan : allPositiveScans.getLeft()) {
          if (chem.getChemical().equals(scan.getTargetChemicalName())) {
            matchinPosScans.add(scan);
            maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxYAxis());
          }
        }
        matchinPosScans.sort(LCMS_SCAN_COMPARATOR);

        List<ScanData<LCMSWell>> matchingNegScans = new ArrayList<>();
        for (ScanData<LCMSWell> scan : allNegativeScans.getLeft()) {
          if (chem.getChemical().equals(scan.getTargetChemicalName())) {
            matchingNegScans.add(scan);
            maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxYAxis());
          }
        }
        matchingNegScans.sort(LCMS_SCAN_COMPARATOR);

        List<ScanData> allScanData = new ArrayList<>();
        allScanData.add(stdScan);
        allScanData.addAll(matchinPosScans);
        allScanData.addAll(matchingNegScans);
        allScanData.add(BLANK_SCAN);

        // Write all the scan data out to a single data file.
        for (ScanData scanData : allScanData) {
          graphLabels.addAll(
              AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, scanData, useFineGrainedMZ, makeHeatmaps, false));
        }
        globalMaxIntensity = Math.max(globalMaxIntensity, maxIntensity);
        // Save one max intensity per graph so we can plot with them later.
        for (int i = 0; i < allScanData.size(); i++) {
          yMaxList.add(maxIntensity);
        }
      }

      // We need to pass the yMax values as an array to the Gnuplotter.
      Double[] yMaxes = yMaxList.toArray(new Double[yMaxList.size()]);
      Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
      if (makeHeatmaps) {
        plotter.plotHeatmap(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]),
            null, fmt, 11.0, 8.5, yMaxes, outImg + ".gnuplot");
      } else {
        plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time",
            null, "intensity", fmt, null, null, yMaxes, outImg + ".gnuplot");
      }
    }
  }

}
