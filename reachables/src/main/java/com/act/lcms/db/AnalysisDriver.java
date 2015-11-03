package com.act.lcms.db;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.CuratedChemical;
import com.act.lcms.db.model.FeedingLCMSWell;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisDriver {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_STRAINS = "s";
  public static final String OPTION_CONSTRUCTS = "c";
  public static final String OPTION_NEGATIVE_STRAINS = "S";
  public static final String OPTION_NEGATIVE_CONSTRUCTS = "C";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_STANDARD_NAME = "sn";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_SEARCH_MZ = "m";
  public static final String OPTION_NO_STANDARD = "ns";
  public static final String OPTION_ANALYZE_PRODUCTS_FOR_CONSTRUCT = "ac";
  public static final String OPTION_FILTER_BY_PLATE_BARCODE = "p";
  public static final String OPTION_USE_HEATMAP = "e";
  public static final String OPTION_FEEDING_ANALYSIS = "f";
  public static final String OPTION_FEEDING_EXTRACT = "x";
  public static final String OPTION_FEEDING_FED_CHEMICAL = "l";

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
            .desc("A prefix for the output data/pdf files")
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
            .hasArg()
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
    add(Option.builder(OPTION_ANALYZE_PRODUCTS_FOR_CONSTRUCT)
            .argName("construct id")
            .desc("A construct whose intermediate/side-reaction products should be searched for in the traces")
            .hasArg()
            .longOpt("search-for-construct-products")
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
    add(Option.builder(OPTION_FEEDING_ANALYSIS)
            .desc("Perform a feeding analysis for some condition; must specify plate, extract, and strain/construct")
            .hasArg()
            .longOpt("feeding-analysis")
    );
    add(Option.builder(OPTION_FEEDING_EXTRACT)
            .desc("Specify the extract for which to perform a feeding analysis")
            .hasArg()
            .longOpt("feeding-extract")
    );
    add(Option.builder(OPTION_FEEDING_FED_CHEMICAL)
            .desc("Specify the fed chemical group for which to perform the feeding analysis")
            .hasArg()
            .longOpt("feeding-chemical")
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


  /**
   * Process a list of wells (LCMS or Standard), producing a list of scan objects that encapsulate the plate,
   * scan file, and masses for that well.
   * @param db The DB from which to extract plate data.
   * @param lcmsDir The directory where the LCMS scans live.
   * @param searchMZs A list of target M/Zs to search for in the scans (see API for {@link MS1}.
   * @param kind The role of this well in this analysis (standard, positive sample, negative control).
   * @param plateCache A hash of Plates already accessed from the DB.
   * @param samples A list of wells to process.
   * @param <T> The PlateWell type whose scans to process.
   * @return A list of ScanData objects that wraps the objects required to produce a graph for each specified well.
   * @throws Exception
   */
  public static <T extends PlateWell<T>> Pair<List<ScanData<T>>, Double> processScans(
      DB db, File lcmsDir, List<Pair<String, Double>> searchMZs, ScanData.KIND kind, HashMap<Integer, Plate> plateCache,
      List<T> samples, boolean useFineGrainedMZTolerance, Set<String> includeIons, Set<String> excludeIons)
      throws Exception {
    Double maxIntensity = 0.0d;
    List<ScanData<T>> allScans = new ArrayList<>(samples.size());
    for (T well : samples) {
      // The foreign key constraint on wells ensure that plate will be non-null.
      Plate plate = plateCache.get(well.getPlateId());
      if (plate == null) {
        plate = Plate.getPlateById(db, well.getPlateId());
        plateCache.put(plate.getId(), plate);
      }
      System.out.format("Processing LCMS well %s %s\n", plate.getBarcode(), well.getCoordinatesString());

      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        System.err.format("WARNING: No scan files available for %s %s\n",
            plate.getBarcode(), well.getCoordinatesString());
        continue;
      }

      for (ScanFile sf : scanFiles) {
        if (sf.getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
          System.err.format("Skipping scan file with non-NetCDF format: %s\n", sf.getFilename());
          continue;
        }
        File localScanFile = new File(lcmsDir, sf.getFilename());
        if (!localScanFile.exists() && localScanFile.isFile()) {
          System.err.format("WARNING: could not find regular file at expected path: %s\n",
              localScanFile.getAbsolutePath());
          continue;
        }

        MS1 mm = new MS1(useFineGrainedMZTolerance);
        for (Pair<String, Double> searchMZ : searchMZs) {
          Map<String, Double> metlinMasses =
              Utils.filterMasses(mm.getIonMasses(searchMZ.getRight(), sf.getMode().toString().toLowerCase()),
                  includeIons, excludeIons);
          MS1.MS1ScanResults ms1ScanResults = mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
          maxIntensity = Math.max(ms1ScanResults.getMaxIntensityAcrossIons(), maxIntensity);
          System.out.format("Max intensity for target %s in %s is %f\n",
              searchMZ.getLeft(), sf.getFilename(), ms1ScanResults.getMaxIntensityAcrossIons());
          // TODO: purge the MS1 spectra from ms1ScanResults if this ends up hogging too much memory.
          allScans.add(new ScanData<T>(kind, plate, well, sf, searchMZ.getLeft(), metlinMasses, ms1ScanResults));
        }
      }
    }
    return Pair.of(allScans, maxIntensity);
  }


  /**
   * Write the time/intensity data for a given scan to an output stream.
   *
   * Note that the signature of ScanData is intentionally weakened to allow us to conditionally handle LCMSWell or
   * StandardWell objects contained in scanData.
   *
   * @param fos The output stream to which to write the time/intensity data.
   * @param lcmsDir The directory where the LCMS scan data can be found.
   * @param maxIntensity The maximum intensity for all scans in the ultimate graph to be produced.
   * @param scanData The scan data whose values will be written.
   * @return A list of graph labels for each LCMS file in the scan.
   * @throws Exception
   */
  public static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity,
                                            ScanData scanData, boolean useFineGrainedMZTolerance,
                                            boolean makeHeatmaps, boolean applyThreshold)
      throws Exception {
    if (ScanData.KIND.BLANK == scanData.getKind()) {
      return Collections.singletonList(Gnuplotter.DRAW_SEPARATOR);
    }

    Plate plate = scanData.getPlate();
    ScanFile sf = scanData.getScanFile();
    Map<String, Double> metlinMasses = scanData.getMetlinMasses();

    MS1 mm = new MS1(useFineGrainedMZTolerance);
    File localScanFile = new File(lcmsDir, sf.getFilename());

    MS1.MS1ScanResults ms1ScanResults = mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
    List<String> ionLabels = mm.writeMS1Values(
        ms1ScanResults.getIonsToSpectra(), maxIntensity, metlinMasses, fos, makeHeatmaps, applyThreshold);
    System.out.format("Scan for target %s has ion labels: %s\n", scanData.getTargetChemicalName(),
        StringUtils.join(ionLabels, ", "));

    List<String> graphLabels = new ArrayList<>(ionLabels.size());
    if (scanData.getWell() instanceof LCMSWell) {
      for (String label : ionLabels) {
        LCMSWell well = (LCMSWell)scanData.getWell();
        String l = String.format("%s (%s fed %s) @ %s %s %s, %s %s",
            well.getComposition(), well.getMsid(),
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
            scanData.getTargetChemicalName(),
            label
        );
        System.out.format("Adding graph w/ label %s\n", l);
        graphLabels.add(l);
      }
    } else if (scanData.getWell() instanceof StandardWell) {
      for (String label : ionLabels) {
        StandardWell well = (StandardWell)scanData.getWell();
        String l = String.format("Standard %s @ %s %s %s, %s %s",
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
            scanData.getTargetChemicalName(),
            label
        );
        System.out.format("Adding graph w/ label %s\n", l);
        graphLabels.add(l);
      }
    } else {
      throw new RuntimeException(
          String.format("Graph request for well type %s", scanData.well.getClass().getCanonicalName()));
    }

    System.out.format("Done processing file at %s\n", localScanFile.getAbsolutePath());
    return graphLabels;
  }


  private static void performFeedingAnalysis(DB db, String lcmsDir,
                                             Set<String> searchIons, String searchMassStr, String plateBarcode,
                                             String strainOrConstruct, String extract, String feedingCondition,
                                             String outPrefix, String fmt)
      throws SQLException, Exception {
    Plate p = Plate.getPlateByBarcode(db, plateBarcode);
    if (p == null) {
      throw new RuntimeException(String.format("Unable to find plate with barcode %s", plateBarcode));
    }
    if (p.getContentType() != Plate.CONTENT_TYPE.FEEDING_LCMS) {
      throw new RuntimeException(String.format("Plate with barcode %s is not a feeding plate (%s)",
          plateBarcode, p.getContentType()));
    }
    List<FeedingLCMSWell> allPlateWells = FeedingLCMSWell.getInstance().getFeedingLCMSWellByPlateId(db, p.getId());
    if (allPlateWells == null || allPlateWells.size() == 0) {
      throw new RuntimeException(String.format("No feeding LCMS wells available for plate %s", p.getBarcode()));
    }

    List<FeedingLCMSWell> relevantWells = new ArrayList<>();
    for (FeedingLCMSWell well : allPlateWells) {
      if (!well.getMsid().equals(strainOrConstruct) && !well.getComposition().equals(strainOrConstruct)) {
        // Ignore wells that don't have the right strain/construct (though we assume the whole plate shares one).
        continue;
      }

      if (!well.getExtract().equals(extract)) {
        // Filter by extract.
        continue;
      }

      if (!well.getChemical().equals(feedingCondition)) {
        // Filter by fed chemical.
        continue;
      }

      relevantWells.add(well);
    }

    Collections.sort(relevantWells, new Comparator<FeedingLCMSWell>() {
      @Override
      public int compare(FeedingLCMSWell o1, FeedingLCMSWell o2) {
        // Assume concentration is never null.
        return o1.getConcentration().compareTo(o2.getConcentration());
      }
    });

    Map<FeedingLCMSWell, ScanFile> wellsToScanFiles = new HashMap<>();
    Set<String> constructs = new HashSet<>(1);
    for (FeedingLCMSWell well : relevantWells) {
      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files for well at %s %s\n", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      if (scanFiles.size() > 1) {
        System.err.format("WARNING: found multiple scan files for %s %s, using first\n",
            p.getBarcode(), well.getCoordinatesString());
      }
      while (scanFiles.size() > 0 && scanFiles.get(0).getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
        scanFiles.remove(0);
      }
      if (scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files with valid format for %s %s\n",
            p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      // All of the extracted wells should be unique, so there should be no collisions here.
      wellsToScanFiles.put(well, scanFiles.get(0));
      constructs.add(well.getComposition());
    }

    Pair<String, Double> searchMass = null;
    if (searchMassStr != null) {
      searchMass = Utils.extractMassFromString(db, searchMassStr);
    }
    if (searchMass == null) {
      if (constructs.size() != 1) {
        throw new RuntimeException(String.format(
            "Found multiple growth targets for feeding analysis when no mass specified: %s",
            StringUtils.join(constructs, ", ")));
      }
      String constructName = constructs.iterator().next();
      CuratedChemical cc = Utils.extractTargetForConstruct(db, constructName);
      if (cc == null) {
        throw new RuntimeException(String.format("Unable to find curated chemical for construct %s", constructName));
      }
      System.out.format("Using target %s of construct %s as search mass (%f)\n",
          cc.getName(), constructName, cc.getMass());
      searchMass = Pair.of(cc.getName(), cc.getMass());
    }

    MS1 c = new MS1();
    // TODO: use configurable or scan-file derived ion mode.
    Map<String, Double> metlinMasses = c.getIonMasses(searchMass.getValue(), "pos");

    if (searchIons == null || searchIons.size() == 0) {
      System.err.format("WARNING: no search ion defined, defaulting to M+H\n");
      searchIons = Collections.singleton("M+H");
    } else if (searchIons.size() > 1) {
      throw new RuntimeException("Multiple ions specified for feeding experiment, only one is allowed");
    }
    String searchIon = searchIons.iterator().next();

    List<Pair<Double, MS1.MS1ScanResults>> rampUp = new ArrayList<>();
    for (FeedingLCMSWell well : relevantWells) {
      ScanFile scanFile = wellsToScanFiles.get(well);
      if (scanFile == null) {
        System.err.format("WARNING: no scan file available for %s %s", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      File localScanFile = new File(lcmsDir, scanFile.getFilename());
      if (!localScanFile.exists() && localScanFile.isFile()) {
        System.err.format("WARNING: could not find regular file at expected path: %s\n",
            localScanFile.getAbsolutePath());
        continue;
      }
      System.out.format("Processing scan data at %s\n", localScanFile.getAbsolutePath());
      MS1.MS1ScanResults ms1ScanResults = c.getMS1(metlinMasses, localScanFile.getAbsolutePath());
      Double concentration = well.getConcentration();
      rampUp.add(Pair.of(concentration, ms1ScanResults));
    }

    c.plotFeedings(rampUp, searchIon, outPrefix, fmt);
  }

  public static class ScanData<T extends PlateWell<T>> {
    public enum KIND {
      STANDARD,
      POS_SAMPLE,
      NEG_CONTROL,
      BLANK,
    }

    KIND kind;
    Plate plate;
    T well;
    ScanFile scanFile;
    String targetChemicalName;
    Map<String, Double> metlinMasses;
    MS1.MS1ScanResults ms1ScanResults;

    public ScanData(KIND kind, Plate plate, T well, ScanFile scanFile, String targetChemicalName,
                    Map<String, Double> metlinMasses, MS1.MS1ScanResults ms1ScanResults) {
      this.kind = kind;
      this.plate = plate;
      this.well = well;
      this.scanFile = scanFile;
      this.targetChemicalName = targetChemicalName;
      this.metlinMasses = metlinMasses;
      this.ms1ScanResults = ms1ScanResults;
    }

    public KIND getKind() {
      return kind;
    }

    public Plate getPlate() {
      return plate;
    }

    public T getWell() {
      return well;
    }

    public ScanFile getScanFile() {
      return scanFile;
    }

    public String getTargetChemicalName() {
      return targetChemicalName;
    }

    public Map<String, Double> getMetlinMasses() {
      return metlinMasses;
    }

    public MS1.MS1ScanResults getMs1ScanResults() {
      return ms1ScanResults;
    }

    @Override
    public String toString() {
      return String.format("%s: %s @ %s, file %s",
          kind, plate.getBarcode(), well.getCoordinatesString(), scanFile.getFilename());
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

    File lcmsDir = new File(cl.getOptionValue("d"));
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
        // All filtering on barcode even if we couldn't find any in the DB.
      }

      System.out.format("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      if (cl.hasOption(OPTION_FEEDING_ANALYSIS)) {
        if (!(cl.hasOption(OPTION_FEEDING_EXTRACT) && cl.hasOption(OPTION_FEEDING_FED_CHEMICAL))) {
          throw new RuntimeException("Must specify both extract and fed chemical for feeding analysis");
        }

        System.out.format("Running feeding analysis\n");
        performFeedingAnalysis(db, cl.getOptionValue(OPTION_DIRECTORY),
            includeIons, cl.getOptionValue(OPTION_SEARCH_MZ),
            cl.getOptionValue(OPTION_FILTER_BY_PLATE_BARCODE), cl.getOptionValue(OPTION_FEEDING_ANALYSIS),
            cl.getOptionValue(OPTION_FEEDING_EXTRACT), cl.getOptionValue(OPTION_FEEDING_FED_CHEMICAL),
            cl.getOptionValue(OPTION_OUTPUT_PREFIX), "pdf");
        return;
      }

      System.out.format("Processing LCMS scans\n");
      //ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);
      String[] negativeStrains = Utils.ensureNonNull(cl.getOptionValues(OPTION_NEGATIVE_STRAINS));
      String[] negativeConstructs = Utils.ensureNonNull(cl.getOptionValues(OPTION_NEGATIVE_CONSTRUCTS));

      Pair<List<LCMSWell>, Set<Integer>> positiveWellsAndPlateIds = Utils.extractWellsAndPlateIds(
          db, cl.getOptionValues(OPTION_STRAINS), cl.getOptionValues(OPTION_CONSTRUCTS), takeSamplesFromPlateIds);
      List<LCMSWell> positiveWells = positiveWellsAndPlateIds.getLeft();
      if (positiveWells.size() == 0) {
        throw new RuntimeException("Found no LCMS wells for specified strains/constructs");
      }
      // Only take negative samples from the plates where we found the positive samples.
      Pair<List<LCMSWell>, Set<Integer>> negativeWellsAndPlateIds =
          Utils.extractWellsAndPlateIds(
              db, cl.getOptionValues(OPTION_NEGATIVE_STRAINS), cl.getOptionValues(OPTION_NEGATIVE_CONSTRUCTS),
              positiveWellsAndPlateIds.getRight());
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
      } else if (cl.hasOption(OPTION_ANALYZE_PRODUCTS_FOR_CONSTRUCT)) {
        List<Pair<ChemicalAssociatedWithPathway, Double>> productMasses =
            Utils.extractMassesForChemicalsAssociatedWithConstruct(
                db, cl.getOptionValue(OPTION_ANALYZE_PRODUCTS_FOR_CONSTRUCT));
        searchMZs = new ArrayList<>(productMasses.size());
        pathwayChems = new ArrayList<>(productMasses.size());
        standardChemicals = new HashSet<>(productMasses.size());
        for (Pair<ChemicalAssociatedWithPathway, Double> productMass : productMasses) {
          String chemName = productMass.getLeft().getChemical();
          searchMZs.add(Pair.of(chemName, productMass.getRight()));
          pathwayChems.add(productMass.getLeft());
          // We assume all standards will appear in the curated chemicals list, but don't add chems we can't find..
          CuratedChemical standardChem = CuratedChemical.getCuratedChemicalByName(db, chemName);
          if (standardChem != null) {
            standardChemicals.add(standardChem);
          } else {
            System.err.format("ERROR: can't find pathway chemical %s in curated chemicals list\n", chemName);
          }
        }
        System.out.format("Searching for intermediate/side-reaction products:\n");
        for (Pair<String, Double> searchMZ : searchMZs) {
          System.out.format("  %s: %.3f\n", searchMZ.getLeft(), searchMZ.getRight());
        }
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
      } else if (!cl.hasOption(OPTION_STANDARD_PLATE_BARCODE)) {
        throw new RuntimeException("A standard plate barcode must be specified if standards are to be used");
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

      /* Process the standard, positive, and negative wells, producing ScanData containers that will allow them to be
       * iterated over for graph writing. */
      HashMap<Integer, Plate> plateCache = new HashMap<>();
      Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
          processScans(db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, standardWells,
              useFineGrainedMZ, includeIons, excludeIons);
      Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans =
          processScans(db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, plateCache, positiveWells,
              useFineGrainedMZ, includeIons, excludeIons);
      Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans =
          processScans(db, lcmsDir, searchMZs, ScanData.KIND.NEG_CONTROL, plateCache, negativeWells,
              useFineGrainedMZ, includeIons, excludeIons);


      String fmt = "pdf";
      String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + fmt;
      String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + ".data";
      System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);

      if (cl.hasOption(OPTION_ANALYZE_PRODUCTS_FOR_CONSTRUCT)) {
        produceLCMSPathwayHeatmaps(lcmsDir, outData, outImg, pathwayChems, allStandardScans, allPositiveScans,
            allNegativeScans, fontScale, useFineGrainedMZ, cl.hasOption(OPTION_USE_HEATMAP), ScanFile.SCAN_MODE.POS);
      } else {
        produceLCMSSearchPlots(lcmsDir, outData, outImg, allStandardScans, allPositiveScans,
            allNegativeScans, fontScale, useFineGrainedMZ, cl.hasOption(OPTION_USE_HEATMAP));
      }
    }
  }

  private static void produceLCMSSearchPlots(File lcmsDir, String outData, String outImg,
                                             Pair<List<ScanData<StandardWell>>, Double> allStandardScans,
                                             Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans,
                                             Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans,
                                             Double fontScale, boolean useFineGrainedMZ, boolean makeHeatmaps)
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
        graphLabels.addAll(writeScanData(fos, lcmsDir, maxIntensity, scanData, useFineGrainedMZ, makeHeatmaps, true));
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

  private static final Comparator<ScanData<LCMSWell>> LCMS_SCAN_COMPARATOR = new Comparator<ScanData<LCMSWell>>() {
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
              maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxIntensityAcrossIons());
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
            maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxIntensityAcrossIons());
          }
        }
        matchinPosScans.sort(LCMS_SCAN_COMPARATOR);

        List<ScanData<LCMSWell>> matchingNegScans = new ArrayList<>();
        for (ScanData<LCMSWell> scan : allNegativeScans.getLeft()) {
          if (chem.getChemical().equals(scan.getTargetChemicalName())) {
            matchingNegScans.add(scan);
            maxIntensity = Math.max(maxIntensity, scan.getMs1ScanResults().getMaxIntensityAcrossIons());
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
              writeScanData(fos, lcmsDir, maxIntensity, scanData, useFineGrainedMZ, makeHeatmaps, false));
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
