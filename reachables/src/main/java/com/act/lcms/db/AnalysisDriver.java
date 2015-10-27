package com.act.lcms.db;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1MetlinMasses;
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

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class applies the MS1MetlinMass LCMS analysis to a combination of ",
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

    // DB connection options.
    add(Option.builder()
            .argName("database url")
            .desc("The url to use when connecting to the LCMS db")
            .hasArg()
            .longOpt("db-url")
    );
    add(Option.builder("u")
            .argName("database user")
            .desc("The LCMS DB user")
            .hasArg()
            .longOpt("db-user")
    );
    add(Option.builder("p")
            .argName("database password")
            .desc("The LCMS DB password")
            .hasArg()
            .longOpt("db-pass")
    );
    add(Option.builder("H")
            .argName("database host")
            .desc(String.format("The LCMS DB host (default = %s)", DB.DEFAULT_HOST))
            .hasArg()
            .longOpt("db-host")
    );
    add(Option.builder("P")
            .argName("database port")
            .desc(String.format("The LCMS DB port (default = %d)", DB.DEFAULT_PORT))
            .hasArg()
            .longOpt("db-port")
    );
    add(Option.builder("db")
            .argName("database name")
            .desc(String.format("The LCMS DB name (default = %s)", DB.DEFAULT_DB_NAME))
            .hasArg()
            .longOpt("db-name")
    );

    add(Option.builder()
            .argName("font scale")
            .desc("A Gnuplot fontscale value, should be between 0.1 and 0.5 (0.4 works if the graph text is large")
            .hasArg()
            .longOpt("font-scale")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
    );
  }};

  /**
   * Finds the target chemical for a given set of wells, assuming there will be exactly one shared for all positive
   * wells in the list.
   * @param db The database in which to look up constructs/chemicals.
   * @param positiveWells The list of wells whose standards to find.
   * @return An object representing the target chemical for the specified wells.
   * @throws SQLException
   */
  private static Set<CuratedChemical> extractTargetsForWells(DB db, List<LCMSWell> positiveWells) throws SQLException {
    Set<CuratedChemical> chemicals = new HashSet<>();
    for (LCMSWell well : positiveWells) {
      ConstructEntry cme =
          ConstructEntry.getCompositionMapEntryByCompositionId(db, well.getComposition());
      if (cme == null) {
        System.err.format("WARNING: No construct -> chemical mapping for %s\n", well.getComposition());
        continue;
      }
      CuratedChemical cc = CuratedChemical.getCuratedChemicalByName(db, cme.getTarget());
      if (cc == null) {
        System.err.format("WARNING: No curated chemical entry for %s/%s\n", cme.getCompositionId(), cme.getTarget());
        continue;
      }
      if (cc.getMass() <= 0.0d) {
        System.err.format("WARNING: Invalid mass for chemical %s/%s (%f)\n",
            cme.getCompositionId(), cc.getName(), cc.getMass());
        continue;
      }

      chemicals.add(cc);
    }
    return chemicals;
  }

  private static CuratedChemical requireOneTarget(DB db, List<LCMSWell> wells) throws SQLException {
    Set<CuratedChemical> chemicals = extractTargetsForWells(db, wells);
    if (chemicals.size() > 1) {
      // TODO: is there a foreach approach that we can use here that won't break backwards compatibility?
      List<String> chemicalNames = new ArrayList<>(chemicals.size());
      for (CuratedChemical chemical : chemicals) {
        chemicalNames.add(chemical.getName());
      }
      throw new RuntimeException(String.format("Found multiple target chemicals where one required: %s",
          StringUtils.join(chemicalNames, ", ")));
    } else if (chemicals.size() < 1) {
      return null;
    }
    return chemicals.iterator().next();
  }

  /**
   * Process a list of wells (LCMS or Standard), producing a list of scan objects that encapsulate the plate,
   * scan file, and masses for that well.
   * @param db The DB from which to extract plate data.
   * @param lcmsDir The directory where the LCMS scans live.
   * @param searchMz The target M/Z to search for in the scans (see API for {@link MS1MetlinMasses}.
   * @param kind The role of this well in this analysis (standard, positive sample, negative control).
   * @param plateCache A hash of Plates already accessed from the DB.
   * @param samples A list of wells to process.
   * @param <T> The PlateWell type whose scans to process.
   * @return A list of ScanData objects that wraps the objects required to produce a graph for each specified well.
   * @throws Exception
   */
  private static <T extends PlateWell<T>> Pair<List<ScanData<T>>, Double> processScans(
      DB db, File lcmsDir, Double searchMz, ScanData.KIND kind, HashMap<Integer, Plate> plateCache,
      List<T> samples) throws Exception {
    MS1MetlinMasses c = new MS1MetlinMasses();
    Double maxIntensity = 0.0d;
    List<ScanData<T>> allScans = new ArrayList<>(samples.size());
    for (PlateWell<T> well : samples) {
      // The foreign key constraint on wells ensure that plate will be non-null.
      Plate plate = plateCache.get(well.getPlateId());
      if (plate == null) {
        plate = Plate.getPlateById(db, well.getPlateId());
        plateCache.put(plate.getId(), plate);
      }
      System.out.format("processing LCMS well %s %d x %d\n",
          plate.getBarcode(), well.getPlateRow(), well.getPlateColumn());

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

        MS1MetlinMasses mm = new MS1MetlinMasses();
        Map<String, Double> metlinMasses = mm.getIonMasses(searchMz, sf.getMode().toString().toLowerCase());
        Pair<Map<String, List<MS1MetlinMasses.XZ>>, Double> ms1s_max =
            mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
        maxIntensity = Math.max(ms1s_max.getRight(), maxIntensity);
        System.out.format("Max intensity for %s is %f\n", sf.getFilename(), ms1s_max.getRight());

        allScans.add(new ScanData<T>(kind, plate, well, sf, metlinMasses));
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
  private static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity, ScanData scanData, boolean makeHeatmaps)
      throws Exception {
    Plate plate = scanData.getPlate();
    ScanFile sf = scanData.getScanFile();
    Map<String, Double> metlinMasses = scanData.getMetlinMasses();

    MS1MetlinMasses mm = new MS1MetlinMasses();
    File localScanFile = new File(lcmsDir, sf.getFilename());

    Pair<Map<String, List<MS1MetlinMasses.XZ>>, Double> ms1s_max =
        mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
    List<String> ionLabels = mm.writeMS1Values(ms1s_max.getLeft(), maxIntensity, metlinMasses, fos, makeHeatmaps);

    List<String> graphLabels = new ArrayList<>(ionLabels.size());
    if (scanData.getWell() instanceof LCMSWell) {
      for (String label : ionLabels) {
        LCMSWell well = (LCMSWell)scanData.getWell();
        String l = String.format("%s (%s fed %s) @ %s %s %s, %s",
            well.getComposition(), well.getMsid(),
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
            label
        );
        System.out.format("Adding graph w/ label %s\n", l);
        graphLabels.add(l);
      }
    } else if (scanData.getWell() instanceof StandardWell) {
      for (String label : ionLabels) {
        StandardWell well = (StandardWell)scanData.getWell();
        String l = String.format("Standard %s @ %s %s %s, %s",
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
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

  private static String[] ensureNonNull(String[] val) {
    return val == null ? new String[0] : val;
  }

  /**
   * Find a well containing the specified chemical in the plate with a given barcode.
   * @param db A DB containing plate/well data.
   * @param standardPlateBarcode The barcode of the plate in which to search.
   * @param standardName The name of the chemical to find.
   * @return The StandardWell in the specified plate that contains the specified chemical.
   * @throws SQLException
   */
  private static StandardWell extractStandardWellFromPlate(DB db, String standardPlateBarcode, String standardName)
      throws SQLException {
    Plate standardPlate = Plate.getPlateByBarcode(db, standardPlateBarcode);
    if (standardPlate == null) {
      throw new RuntimeException(
          String.format("Unable to find standard plate with barcode %s", standardPlateBarcode));
    }
    if (standardPlate.getContentType() != Plate.CONTENT_TYPE.STANDARD) {
      throw new RuntimeException(String.format("Plate with barcode %s has content type %s, expected %s",
          standardPlateBarcode, standardPlate.getContentType(), Plate.CONTENT_TYPE.STANDARD));
    }
    List<StandardWell> standardWells = StandardWell.getInstance().getByPlateId(db, standardPlate.getId());
    for (StandardWell well : standardWells) {
      if (standardName.equals(well.getChemical())) {
        System.out.format("Found matching standard well at %s (%s)\n", well.getCoordinatesString(), well.getChemical());
        return well;
      }
    }
    throw new RuntimeException(String.format("Unable to find standard chemical %s in plate %s",
        standardName, standardPlateBarcode));
  }

  public static class ScanData<T extends PlateWell<T>> {
    public enum KIND {
      STANDARD,
      POS_SAMPLE,
      NEG_CONTROL,
    }

    KIND kind;
    Plate plate;
    PlateWell<T> well;
    ScanFile scanFile;
    Map<String, Double> metlinMasses;

    public ScanData(KIND kind, Plate plate, PlateWell<T> well, ScanFile scanFile, Map<String, Double> metlinMasses) {
      this.kind = kind;
      this.plate = plate;
      this.well = well;
      this.scanFile = scanFile;
      this.metlinMasses = metlinMasses;
    }

    public KIND getKind() {
      return kind;
    }

    public Plate getPlate() {
      return plate;
    }

    public PlateWell<T> getWell() {
      return well;
    }

    public ScanFile getScanFile() {
      return scanFile;
    }

    public Map<String, Double> getMetlinMasses() {
      return metlinMasses;
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

    DB db = null;
    try {
      if (cl.hasOption("db-url")) {
        db = new DB().connectToDB(cl.getOptionValue("db-url"));
      } else {
        Integer port = null;
        if (cl.getOptionValue("P") != null) {
          port = Integer.parseInt(cl.getOptionValue("P"));
        }
        db = new DB().connectToDB(cl.getOptionValue("H"), port, cl.getOptionValue("N"),
            cl.getOptionValue("u"), cl.getOptionValue("p"));
      }

      System.out.format("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      System.out.format("Processing LCMS scans\n");
      //ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);
      String[] strains = ensureNonNull(cl.getOptionValues(OPTION_STRAINS));
      String[] constructs = ensureNonNull(cl.getOptionValues(OPTION_CONSTRUCTS));
      String[] negativeStrains = ensureNonNull(cl.getOptionValues(OPTION_NEGATIVE_STRAINS));
      String[] negativeConstructs = ensureNonNull(cl.getOptionValues(OPTION_NEGATIVE_CONSTRUCTS));

      Set<Integer> seenWellIds = new HashSet<>();
      // Track the plates that we've seen so we can restrict the negative controls to the same plates as the positives.
      Set<Integer> positivePlateIds = new HashSet<>();

      List<LCMSWell> positiveWells = new ArrayList<>();
      for (String s : strains) {
        List<LCMSWell> res = LCMSWell.getInstance().getByStrain(db, s);
        for (LCMSWell well : res) {
          if (!seenWellIds.contains(well.getId())) {
            positiveWells.add(well);
            seenWellIds.add(well.getId());
            positivePlateIds.add(well.getPlateId());
          }
        }
      }
      for (String c : constructs) {
        List<LCMSWell> res = LCMSWell.getInstance().getByConstructID(db, c);
        for (LCMSWell well : res) {
          if (!seenWellIds.contains(well.getId())) {
            positiveWells.add(well);
            seenWellIds.add(well.getId());
            positivePlateIds.add(well.getPlateId());
          }
        }
      }

      if (positiveWells.size() == 0) {
        throw new RuntimeException(String.format("Found no LCMS wells for strains/constructs: %s/%s",
            StringUtils.join(strains, ", "), StringUtils.join(constructs, ", ")));
      }

      List<LCMSWell> negativeWells = new ArrayList<>();
      for (String s : negativeStrains) {
        List<LCMSWell> res = LCMSWell.getInstance().getByStrain(db, s);
        for (LCMSWell well : res) {
          if (!seenWellIds.contains(well.getId()) &&
              positivePlateIds.contains(well.getPlateId())) {
            negativeWells.add(well);
            seenWellIds.add(well.getId());
            break; // Just take the first negative example that we haven't seen yet.
          }
        }
      }
      for (String c : negativeConstructs) {
        List<LCMSWell> res = LCMSWell.getInstance().getByConstructID(db, c);
        for (LCMSWell well : res) {
          if (!seenWellIds.contains(well.getId()) &&
              positivePlateIds.contains(well.getPlateId())) {
            negativeWells.add(well);
            seenWellIds.add(well.getId());
            break;
          }
        }
      }

      // Extract the reference MZ that will be used in the LCMS trace processing.
      Double searchMZ;
      Set<CuratedChemical> standardChemicals = null;
      if (cl.hasOption(OPTION_SEARCH_MZ)) {
        // Assume mz can be an FP number of a chemical name.
        String massStr = cl.getOptionValue(OPTION_SEARCH_MZ);
        try {
          searchMZ = Double.parseDouble(massStr);
          System.out.format("Using raw M/Z value: %f\n", searchMZ);
        } catch (IllegalArgumentException e) {
          CuratedChemical targetChemical = CuratedChemical.getCuratedChemicalByName(db, massStr);
          if (targetChemical == null) {
            throw new RuntimeException(
                String.format("Unable to parse or find chemical name for reference m/z: %s", massStr));
          }
          searchMZ = targetChemical.getMass();
          System.out.format("Using reference M/Z for specified chemical %s (%f)\n",
              targetChemical.getName(), searchMZ);
        }
        standardChemicals = extractTargetsForWells(db, positiveWells);
      } else {
        CuratedChemical targetChemical = requireOneTarget(db, positiveWells);
        if (targetChemical == null) {
          throw new RuntimeException(
              "Unable to find a curated chemical entry for specified strains'/constructs' targets.  " +
                  "Please specify a chemical name or m/z explicitly or update the curated chemicals list in the DB.");
        }
        searchMZ = targetChemical.getMass();
        System.out.format("Using reference M/Z for positive target %s (%f)\n",
            targetChemical.getName(), searchMZ);
        standardChemicals = Collections.singleton(targetChemical);
      }

      // Look up the standard by name, or use the target if none is specified.
      List<StandardWell> standardWells = null;
      if (cl.hasOption(OPTION_NO_STANDARD)) {
        System.err.format("WARNING: skipping standard comparison (no-standard option specified)\n");
        standardWells = new ArrayList<>(0);
      } else if (cl.hasOption(OPTION_STANDARD_NAME)) {
        String standardName = cl.getOptionValue(OPTION_STANDARD_NAME);
        System.out.format("Using explicitly specified standard %s\n", standardName);
        standardWells = Collections.singletonList(
            extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName));
      } else if (standardChemicals != null && standardChemicals.size() > 0) {
        // Default to using the target chemical(s) as a standard if none is specified.
        for (CuratedChemical c : standardChemicals) {
          String standardName = c.getName();
          System.out.format("Searching for well containing standard %s\n", standardName);
          standardWells = Collections.singletonList(
              extractStandardWellFromPlate(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE), standardName));
        }
      }

      String fmt = "pdf";
      String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + fmt;
      String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + ".data";
      System.err.format("Writing combined scan data to %s and graphs to %s\n", outData, outImg);
      boolean makeHeatmaps = true;

      // Generate the data file and graphs.
      try (FileOutputStream fos = new FileOutputStream(outData)) {
        /* Process the standard, positive, and negative wells, producing ScanData containers that will allow them to be
         * iterated over for graph writing. */
        HashMap<Integer, Plate> plateCache = new HashMap<>();
        Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
            processScans(db, lcmsDir, searchMZ, ScanData.KIND.STANDARD, plateCache, standardWells);
        Pair<List<ScanData<LCMSWell>>, Double> allPositiveScans =
            processScans(db, lcmsDir, searchMZ, ScanData.KIND.POS_SAMPLE, plateCache, positiveWells);
        Pair<List<ScanData<LCMSWell>>, Double> allNegativeScans =
            processScans(db, lcmsDir, searchMZ, ScanData.KIND.NEG_CONTROL, plateCache, negativeWells);
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

        // Write all the scan data out to a single data file.
        List<String> graphLabels = new ArrayList<>();
        for (ScanData scanData : allScanData) {
          graphLabels.addAll(writeScanData(fos, lcmsDir, maxIntensity, scanData, makeHeatmaps));
        }

        Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
        if (makeHeatmaps) {
          plotter.plotHeatmap(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time", 
              maxIntensity, "intensity", fmt);
        } else {
          plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time", 
              maxIntensity, "intensity", fmt);
        }
      }
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }
}
