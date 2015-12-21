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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathwayProductAnalysis {
  public static final String DEFAULT_SEARCH_ION = "M+H";

  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_STRAINS = "s";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_NEGATIVE_STRAINS = "S";
  public static final String OPTION_NEGATIVE_CONSTRUCTS = "C";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_STANDARD_WELLS = "sw";
  public static final String OPTION_FILTER_BY_PLATE_BARCODE = "p";
  public static final String OPTION_USE_HEATMAP = "e";
  public static final String OPTION_SEARCH_ION = "i";
  public static final String OPTION_PATHWAY_SEARCH_IONS = "I";
  public static final String OPTION_ALLOW_MISSING_STANDARDS = "M";
  public static final String OPTION_USE_SNR = "r";

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
    add(Option.builder(OPTION_STANDARD_WELLS)
            .argName("standard wells")
            .desc("A list of well coordinates for stanards, either offset in the pathway or a mapping of " +
                "intermediate product to well (like paracetamol=A1,chorismate=C2)")
            .hasArg().valueSeparator(',')
            .longOpt("standard-wells")
    );
    add(Option.builder(OPTION_SEARCH_ION)
            .argName("search ion")
            .desc("The ion for which to search (default is " + DEFAULT_SEARCH_ION +
                "); if used with -" + OPTION_PATHWAY_SEARCH_IONS + ", this will be the default for unspecified steps")
            .hasArg()
            .longOpt("search-ion")
    );
    add(Option.builder(OPTION_PATHWAY_SEARCH_IONS)
            .desc("A list of ions per step, either by offset in the pathway (ultimate target first), or a mapping of " +
                "intermediate product to ion (like paracetamol=M+H,chorismate=M+K)")
            .hasArgs().valueSeparator(',')
            .longOpt("intermediate-ions")
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
    add(Option.builder(OPTION_ALLOW_MISSING_STANDARDS)
            .desc("Don't error when the standard for a pathway step can't be found")
            .longOpt("allow-missing-standards")
    );
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
      List<StandardWell> standardWells = new ArrayList<>();
      if (cl.hasOption(OPTION_STANDARD_WELLS)) {
        Plate standardPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
        Map<Integer, StandardWell> pathwayIdToStandardWell = extractStandardWellsFromOptionsList(
            db, pathwayChems, cl.getOptionValues(OPTION_STANDARD_WELLS), standardPlate);
        for (ChemicalAssociatedWithPathway c : pathwayChems) { // TODO: we can avoid this loop.
          StandardWell well = pathwayIdToStandardWell.get(c.getId());
          if (well != null) {
            standardWells.add(well);
          }
        }
      } else {
        for (ChemicalAssociatedWithPathway c : pathwayChems) {
          String standardName = c.getChemical();
          System.out.format("Searching for well containing standard %s\n", standardName);
          StandardWell sw =
              Utils.extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName,
                  !cl.hasOption(OPTION_ALLOW_MISSING_STANDARDS));
          if (sw != null) {
            standardWells.add(sw);
          }
        }
      }

      boolean useFineGrainedMZ = cl.hasOption("fine-grained-mz");

      Map<Integer, String> searchIons = null;
      Set<String> includeIons = null;
      if (cl.hasOption(OPTION_PATHWAY_SEARCH_IONS)) {
        searchIons = extractPathwayStepIons(pathwayChems, cl.getOptionValues(OPTION_PATHWAY_SEARCH_IONS),
            cl.getOptionValue(OPTION_SEARCH_ION, "M+H"));
        /* This is pretty lazy, but works with the existing API.  Extract all selected ions for all search masses when
         * performing the scan, then filter down to the desired ions for the plot at the end.
         * TODO: specify the masses and scans per sample rather than batching everything together.  It might be slower,
         * but it'll be clearer to read. */
        includeIons = new HashSet<String>(searchIons.values());
      } else if (cl.hasOption(OPTION_SEARCH_ION)) {
        includeIons = Collections.singleton(cl.getOptionValue(OPTION_SEARCH_ION));
      } else {
        includeIons = Collections.singleton("M+H");
      }

      boolean useSNR = cl.hasOption(OPTION_USE_SNR);

      /* Process the standard, positive, and negative wells, producing ScanData containers that will allow them to be
       * iterated over for graph writing. */
      HashMap<Integer, Plate> plateCache = new HashMap<>();
      Set<String> emptySet = new HashSet<>(0);
      Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, standardWells,
              useFineGrainedMZ, includeIons, emptySet, useSNR);
      Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, plateCache, positiveWells,
              useFineGrainedMZ, includeIons, emptySet, useSNR);
      Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans =
          AnalysisHelper.processScans(
              db, lcmsDir, searchMZs, ScanData.KIND.NEG_CONTROL, plateCache, negativeWells,
              useFineGrainedMZ, includeIons, emptySet, useSNR);


      String fmt = "pdf";
      String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + fmt;
      String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + ".data";
      System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

      produceLCMSPathwayHeatmaps(lcmsDir, outData, outImg, pathwayChems, allStandardScans,
          allPositiveScans, allNegativeScans, fontScale, useFineGrainedMZ, cl.hasOption(OPTION_USE_HEATMAP), useSNR,
          ScanFile.SCAN_MODE.POS, searchIons);
    }
  }

  private static Map<Integer, StandardWell> extractStandardWellsFromOptionsList(
      DB db, List<ChemicalAssociatedWithPathway> pathwayChems, String[] optionValues, Plate standardPlate)
      throws SQLException {
    Map<String, String> chemToWellByName = new HashMap<>();
    Map<Integer, String> chemToWellByIndex = new HashMap<>();
    if (optionValues != null && optionValues.length > 0) {
      for (int i = 0; i < optionValues.length; i++) {
        String[] fields = StringUtils.split(optionValues[i], "=");
        if (fields != null && fields.length == 2) {
          if (!MS1.VALID_MS1_IONS.contains(fields[1])) {
            System.err.format("WARNING: found invalid intermediate/ion pair, skipping: %s\n", optionValues[i]);
            continue;
          }
          chemToWellByName.put(fields[0], fields[1]);
        } else {
          chemToWellByIndex.put(i, optionValues[i]);
        }
      }
    }

    Map<Integer, StandardWell> results = new HashMap<>();
    for (int i = 0; i < pathwayChems.size(); i++) {
      ChemicalAssociatedWithPathway chem = pathwayChems.get(i);
      String coords = null;
      if (chemToWellByName.containsKey(chem.getChemical())) {
        coords = chemToWellByName.remove(chem.getChemical());
      } else if (chemToWellByIndex.containsKey(i)) {
        coords = chemToWellByIndex.remove(i);
      }

      Pair<Integer, Integer> intCoords = Utils.parsePlateCoordinates(coords);
      StandardWell well = StandardWell.getInstance().getStandardWellsByPlateIdAndCoordinates(
          db, standardPlate.getId(), intCoords.getLeft(), intCoords.getRight());
      if (well == null) {
        System.err.format("ERROR: Could not find well %s in plate %s\n", coords, standardPlate.getBarcode());
        System.exit(-1);
      } else if (!well.getChemical().equals(chem.getChemical())) {
        System.err.format("WARNING: pathway chemical %s and chemical in specified standard well %s don't match!\n",
            chem.getChemical(), well.getChemical());
      }

      System.out.format("Using standard well %s : %s for pathway chemical %s (step %d)\n",
          standardPlate.getBarcode(), coords, chem.getChemical(), chem.getIndex());

      results.put(chem.getId(), well);
    }

    return results;
  }

  private static Map<Integer, String> extractPathwayStepIons(
      List<ChemicalAssociatedWithPathway> pathwayChems, String[] optionValues, String defaultIon) {
    Map<String, String> pathwayToIonByName = new HashMap<>();
    Map<Integer, String> pathwayToIonByIndex = new HashMap<>();
    if (optionValues != null && optionValues.length > 0) {
      for (int i = 0; i < optionValues.length; i++) {
        String[] fields = StringUtils.split(optionValues[i], "=");
        if (fields != null && fields.length == 2) {
          if (!MS1.VALID_MS1_IONS.contains(fields[1])) {
            System.err.format("WARNING: found invalid intermediate/ion pair, skipping: %s\n", optionValues[i]);
            continue;
          }
          pathwayToIonByName.put(fields[0], fields[1]);
        } else {
          pathwayToIonByIndex.put(i, optionValues[i]);
        }
      }
    }

    Map<Integer, String> results = new HashMap<>();
    for (int i = 0; i < pathwayChems.size(); i++) {
      ChemicalAssociatedWithPathway chem = pathwayChems.get(i);
      String ion = defaultIon;
      if (pathwayToIonByName.containsKey(chem.getChemical())) {
        ion = pathwayToIonByName.remove(chem.getChemical());
      } else if (pathwayToIonByIndex.containsKey(i)) {
        ion = pathwayToIonByIndex.remove(i);
      }
      System.out.format("Using ion %s for pathway chemical %s (step %d)\n", ion, chem.getChemical(), chem.getIndex());
      results.put(chem.getId(), ion);
    }

    if (!(pathwayToIonByName.isEmpty() && pathwayToIonByIndex.isEmpty())) {
      System.err.format("WARNING: unable to assign some pathway ions by name/index:\n");
      for (Map.Entry<String, String> entry : pathwayToIonByName.entrySet()) {
        System.err.format("  %s => %s\n", entry.getKey(), entry.getValue());
      }
      for (Map.Entry<Integer, String> entry : pathwayToIonByIndex.entrySet()) {
        System.err.format("  %d => %s\n", entry.getKey(), entry.getValue());
      }
    }

    return results;
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
                                                boolean useSNR, ScanFile.SCAN_MODE scanMode,
                                                Map<Integer, String> searchIons)
      throws Exception {
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

        String pathwayStepIon = null;
        if (searchIons != null && searchIons.containsKey(chem.getId())) {
          pathwayStepIon = searchIons.get(chem.getId());
        }

        // Extract the first available
        ScanData<StandardWell> stdScan = null;
        for (ScanData<StandardWell> scan : allStandardScans.getLeft()) {
          if (chem.getChemical().equals(scan.getWell().getChemical()) &&
              chem.getChemical().equals(scan.getTargetChemicalName())) {
            if (scanMode == null || scanMode.equals(scan.getScanFile().getMode())) {
              stdScan = scan;
              MS1.MS1ScanResults scanRslts = scan.getMs1ScanResults();
              Double intensity = pathwayStepIon == null ? scanRslts.getMaxYAxis() :
                scanRslts.getMaxIntensityForIon(pathwayStepIon);
              maxIntensity = Math.max(maxIntensity, intensity);
              break;
            }
          }
        }
        if (stdScan == null) {
          System.err.format("WARNING: unable to find standard well scan for chemical %s\n", chem.getChemical());
        }

        List<ScanData<LCMSWell>> matchinPosScans = new ArrayList<>();
        for (ScanData<LCMSWell> scan : allPositiveScans.getLeft()) {
          if (chem.getChemical().equals(scan.getTargetChemicalName())) {
            matchinPosScans.add(scan);
            MS1.MS1ScanResults scanRslts = scan.getMs1ScanResults();
            Double intensity = pathwayStepIon == null ? scanRslts.getMaxYAxis() :
              scanRslts.getMaxIntensityForIon(pathwayStepIon);
            maxIntensity = Math.max(maxIntensity, intensity);
          }
        }
        matchinPosScans.sort(LCMS_SCAN_COMPARATOR);

        List<ScanData<LCMSWell>> matchingNegScans = new ArrayList<>();
        for (ScanData<LCMSWell> scan : allNegativeScans.getLeft()) {
          if (chem.getChemical().equals(scan.getTargetChemicalName())) {
            matchingNegScans.add(scan);
            MS1.MS1ScanResults scanRslts = scan.getMs1ScanResults();
            Double intensity = pathwayStepIon == null ? scanRslts.getMaxYAxis() :
              scanRslts.getMaxIntensityForIon(pathwayStepIon);
            maxIntensity = Math.max(maxIntensity, intensity);
          }
        }
        matchingNegScans.sort(LCMS_SCAN_COMPARATOR);

        List<ScanData> allScanData = new ArrayList<>();
        if (stdScan != null) {
          allScanData.add(stdScan);
        }
        allScanData.addAll(matchinPosScans);
        allScanData.addAll(matchingNegScans);
        allScanData.add(BLANK_SCAN);

        Set<String> pathwayStepIons = pathwayStepIon == null ? null : Collections.singleton(pathwayStepIon);
        // Write all the scan data out to a single data file.
        for (ScanData scanData : allScanData) {
          graphLabels.addAll(
              AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, scanData, useFineGrainedMZ,
                  makeHeatmaps, false, useSNR, pathwayStepIons));
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
