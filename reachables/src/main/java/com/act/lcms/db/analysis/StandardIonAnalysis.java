package com.act.lcms.db.analysis;

import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.ConstructEntry;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardIonResult;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public class StandardIonAnalysis {
  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  public static final String CSV_FORMAT = "csv";
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_STANDARD_CHEMICAL = "sc";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_MEDIUM = "m";
  public static final String OPTION_PLOTTING_DIR = "p";
  public static final String OPTION_OVERRIDE_NO_SCAN_FILE_FOUND = "s";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write a help message."
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
    add(Option.builder(OPTION_CONSTRUCT)
        .argName("construct")
        .desc("The construct whose pathway chemicals should be analyzed")
        .hasArg()
        .longOpt("construct")
    );
    add(Option.builder(OPTION_STANDARD_PLATE_BARCODE)
        .argName("standard plate barcode")
        .desc("The plate barcode to use when searching for a compatible standard")
        .hasArg()
        .longOpt("standard-plate")
    );
    add(Option.builder(OPTION_STANDARD_CHEMICAL)
        .argName("standard chemical")
        .desc("The standard chemical to analyze")
        .hasArg()
        .longOpt("standard-chemical")
    );
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg().required()
        .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_MEDIUM)
        .argName("medium")
        .desc("A name of the medium to search wells by.")
        .hasArg()
        .longOpt("medium")
    );
    add(Option.builder(OPTION_PLOTTING_DIR)
        .argName("plotting directory")
        .desc("The absolute path of the plotting directory")
        .hasArg().required()
        .longOpt("plotting-dir")
    );
    add(Option.builder(OPTION_OVERRIDE_NO_SCAN_FILE_FOUND)
        .argName("override option")
        .desc("Do not fail when the scan file cannot be found")
        .longOpt("override-option")
    );
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  /**
   * This function gets all the best time windows from spectra in water and meoh media, so that they can analyzed
   * by the yeast media samples for snr analysis.
   * @param waterAndMeohSpectra A list of ions to best XZ value.
   * @return A map of ion to list of restricted time windows.
   */
  public static Map<String, List<Double>> getRestrictedTimeWindowsForIonsFromWaterAndMeOHMedia(
      List<LinkedHashMap<String, XZ>> waterAndMeohSpectra) {

    Map<String, List<Double>> ionToRestrictedTimeWindows = new HashMap<>();

    for (LinkedHashMap<String, XZ> entry : waterAndMeohSpectra) {
      for (String ion : entry.keySet()) {
        List<Double> restrictedTimes = ionToRestrictedTimeWindows.get(ion);
        if (restrictedTimes == null) {
          restrictedTimes = new ArrayList<>();
          ionToRestrictedTimeWindows.put(ion, restrictedTimes);
        }
        Double timeValue = entry.get(ion).getTime();
        restrictedTimes.add(timeValue);
      }
    }

    return ionToRestrictedTimeWindows;
  }

  /**
   * Given a construct id (like "pa1"), return the associated ConstructEntry object and a list of the chemical
   * products/byproducts associated with that pathway (including all intermediate and side-reaction products).
   * @param db The DB connection to query.
   * @param constructId The identifier for the constructs whose products should be queried (like "pa1").
   * @return A pair of the ConstructEntry for the specified construct id and a list of chemical products associated
   *         with that pathway.
   * @throws SQLException
   */
  public Pair<ConstructEntry, List<ChemicalAssociatedWithPathway>> getChemicalsForConstruct(DB db, String constructId)
      throws SQLException {
    ConstructEntry construct =
        ConstructEntry.getInstance().getCompositionMapEntryByCompositionId(db, constructId);
    if (construct == null) {
      throw new RuntimeException(String.format("Unable to find construct '%s'", constructId));
    }

    List<ChemicalAssociatedWithPathway> products =
        ChemicalAssociatedWithPathway.getInstance().getChemicalsAssociatedWithPathwayByConstructId(db, constructId);

    return Pair.of(construct, products);
  }

  /**
   * Find all standard wells containing a specified chemical.
   * @param db The DB connection to query.
   * @param pathwayChem The chemical for which to find standard wells.
   * @return A list of standard wells (in any plate) containing the specified chemical.
   * @throws SQLException
   */
  public static List<StandardWell> getStandardWellsForChemical(DB db, String pathwayChem)
      throws SQLException {
    return StandardWell.getInstance().getStandardWellsByChemical(db, pathwayChem);
  }

  /**
   * Find all standard wells containing a specified chemical.
   * @param db The DB connection to query.
   * @param chemical The chemical for which to find standard wells.
   * @param plateId The plateId to filter by.
   * @return A list of standard wells (in any plate) containing the specified chemical.
   * @throws SQLException
   */
  public List<StandardWell> getStandardWellsForChemicalInSpecificPlate(DB db,
                                                                       String chemical,
                                                                       Integer plateId) throws SQLException {
    return StandardWell.getInstance().getStandardWellsByChemicalAndPlateId(db, chemical, plateId);
  }

  /**
   * Find all standard wells containing a specified chemical.
   * @param db The DB connection to query.
   * @param chemical The chemical for which to find standard wells.
   * @param plateId The plateId to filter by.
   * @param medium The medium of the plate to filter by.
   * @return A list of standard wells (in any plate) containing the specified chemical.
   * @throws SQLException
   */
  public List<StandardWell> getStandardWellsForChemicalInSpecificPlateAndMedium(DB db,
                                                                                String chemical,
                                                                                Integer plateId,
                                                                                String medium) throws SQLException {
    return StandardWell.getInstance().getStandardWellsByChemicalAndPlateIdAndMedium(db, chemical, plateId, medium);
  }

  public static List<StandardWell> getViableNegativeControlsForStandardWell(DB db, StandardWell baseStandard)
      throws SQLException, IOException, ClassNotFoundException {
    List<StandardWell> wellsFromSamePlate = StandardWell.getInstance().getByPlateId(db, baseStandard.getPlateId());

    // TODO: take availability of scan files into account here?
    List<StandardWell> candidates = new ArrayList<>();
    for (StandardWell well : wellsFromSamePlate) {
      if (well.getChemical().equals(baseStandard.getChemical())) {
        continue; // Skip wells with the same chemical.
      }

      if (baseStandard.getConcentration() != null && well.getConcentration() != null &&
          !baseStandard.getConcentration().equals(well.getConcentration())) {
        continue; // Skip non-matching concentrations if both wells define concentration.
      }

      if (baseStandard.getMedia() != null && well.getMedia() != null &&
          !baseStandard.getMedia().equals(well.getMedia())) {
        continue; // Skip non-matching media if both wells define media type.
      }
      candidates.add(well);
    }

    return candidates;
  }

  /**
   * Given a standard well and viable negative control candidates, returns a map of mapping of all specified standard
   * wells to scan files sharing the ion modes available for the specified standard well.  For example, if the specified
   * standard well has only positive ion mode scan files available, the map will contain only positive ion mode scan
   * files for that well and all specified negativeCandidate wells.  If both positive and negative ion mode scan files
   * are available for the specified well, then, both positive and negative mode scan files will be included in the map.
   * @param db The DB connection to query.
   * @param primaryStandard The primary standard well being analysed.
   * @param negativeCandidates A list of standard wells that could be used as negative controls in the analysis.
   * @return A map from all specified standard wells (primary and negative controls) to a list of scan files.
   * @throws SQLException
   */
  public Map<StandardWell, List<ScanFile>> getViableScanFilesForStandardWells(
      DB db, StandardWell primaryStandard, List<StandardWell> negativeCandidates) throws SQLException {
    Map<StandardWell, List<ScanFile>> wellToFilesMap = new HashMap<>();
    List<ScanFile> posScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
        db, primaryStandard.getPlateId(), primaryStandard.getPlateRow(), primaryStandard.getPlateColumn());
    wellToFilesMap.put(primaryStandard, posScanFiles);

    Set<ScanFile.SCAN_MODE> viableScanModes = new HashSet<>();
    for (ScanFile file : posScanFiles) {
      viableScanModes.add(file.getMode());
    }

    for (StandardWell well : negativeCandidates) {
      List<ScanFile> allScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      List<ScanFile> viableScanFiles = new ArrayList<>();
      for (ScanFile file : allScanFiles) {
        if (viableScanModes.contains(file.getMode())) {
          viableScanFiles.add(file);
        }
      }
      wellToFilesMap.put(well, viableScanFiles);
    }

    return wellToFilesMap;
  }

  /**
   * This function returns the best SNR values and their times for each metlin ion based on the StandardIonResult
   * datastructure and plots diagnostics.
   * @param lcmsDir The directory where the LCMS scan data can be found.
   * @param db The DB connection to query.
   * @param positiveStandardWell This is the positive standard well against which the snr comparison is done.
   * @param negativeStandardWells These are the negative standard wells which are used for benchmarking.
   * @param plateCache A hash of Plates already accessed from the DB.
   * @param chemical This is chemical of interest we are running ion analysis against.
   * @param plottingDir This is the directory where the plotting diagnostics will live.
   * @param restrictedTimeWindows This map of ion to list of doubles represent time windows over which the analysis has
   *                              to be done.
   * @return The StandardIonResult datastructure which contains the standard ion analysis results.
   * @throws Exception
   */
  public static StandardIonResult getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(
      File lcmsDir, DB db, StandardWell positiveStandardWell, List<StandardWell> negativeStandardWells,
      HashMap<Integer, Plate> plateCache, String chemical, String plottingDir, Map<String, List<Double>> restrictedTimeWindows)
      throws Exception {
    Plate plate = plateCache.get(positiveStandardWell.getPlateId());

    if (plate == null) {
      plate = Plate.getPlateById(db, positiveStandardWell.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    List<Pair<String, Double>> searchMZs;
    Pair<String, Double> searchMZ = Utils.extractMassFromString(db, chemical);
    if (searchMZ != null) {
      searchMZs = Collections.singletonList(searchMZ);
    } else {
      throw new RuntimeException("Could not find Mass Charge value for " + chemical);
    }

    List<StandardWell> allWells = new ArrayList<>();
    allWells.add(positiveStandardWell);
    allWells.addAll(negativeStandardWells);

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = AnalysisHelper.readScanData(
        db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, allWells, false, null, null,
        USE_SNR_FOR_LCMS_ANALYSIS, positiveStandardWell.getChemical());

    if (peakData.getIonList().size() == 0) {
      return null;
    }

    LinkedHashMap<String, XZ> snrResults =
        WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNR(peakData, chemical, restrictedTimeWindows);

    String bestMetlinIon = AnalysisHelper.getBestMetlinIonFromPossibleMappings(snrResults);

    Map<String, String> plottingFileMappings =
        peakData.plotPositiveAndNegativeControlsForEachMetlinIon(searchMZ, plottingDir, chemical, allWells);

    StandardIonResult result = new StandardIonResult();
    result.setChemical(chemical);
    result.setAnalysisResults(snrResults);
    result.setStandardWellId(positiveStandardWell.getId());
    result.setPlottingResultFilePaths(plottingFileMappings);
    result.setBestMetlinIon(bestMetlinIon);
    return result;
  }

  /**
   * This function returns the best metlion ions of the SNR analysis for each well.
   * @param chemical - This is chemical of interest we are running ion analysis against.
   * @param lcmsDir - The directory where the LCMS scan data can be found.
   * @param db
   * @param standardWells - The standard wells over which the analysis is done. This list is sorted in the following
   *                      order: wells in water or meoh are processed first, followed by everything else. This ordering
   *                      is required since the analysis on yeast media depends on the analysis of results in water/meoh.
   * @param plottingDir - This is the directory where the plotting diagnostics will live.
   * @return A mapping of the well that was analyzed to the standard ion result.
   * @throws Exception
   */
  public static Map<StandardWell, StandardIonResult> getBestMetlinIonsForChemical(
      String chemical, File lcmsDir, DB db, List<StandardWell> standardWells, String plottingDir) throws Exception {

    Map<StandardWell, StandardIonResult> result = new HashMap<>();
    List<LinkedHashMap<String, XZ>> waterAndMeohSpectra = new ArrayList<>();

    for (StandardWell wellToAnalyze : standardWells) {
      List<StandardWell> negativeControls =
          StandardIonAnalysis.getViableNegativeControlsForStandardWell(db, wellToAnalyze);

      StandardIonResult cachingResult;
      if (StandardWell.doesMediaContainYeastExtract(wellToAnalyze.getMedia())) {
        // Since the standard wells are sorted in a way that the Water and MeOH media wells are analyzed first, we are
        // guaranteed to get the restricted time windows from these wells for the yeast media analysis.
        // TODO: Find a better way of doing this. There is a dependency on other ion analysis from other media and the way to
        // achieve this is by caching those ion runs first before the yeast media analysis. However, this algorithm is
        // dependant on which sequence gets analyzed first, which is brittle.
        Map<String, List<Double>> restrictedTimeWindows =
            getRestrictedTimeWindowsForIonsFromWaterAndMeOHMedia(waterAndMeohSpectra);

        cachingResult = StandardIonResult.getForChemicalAndStandardWellAndNegativeWells(
            lcmsDir, db, chemical, wellToAnalyze, negativeControls, plottingDir, restrictedTimeWindows);
      } else if (StandardWell.isMediaMeOH(wellToAnalyze.getMedia()) || StandardWell.isMediaWater(wellToAnalyze.getMedia())) {
        // If the media is not yeast, there is no time window restrictions, hence the last argument is null.
        cachingResult = StandardIonResult.getForChemicalAndStandardWellAndNegativeWells(
            lcmsDir, db, chemical, wellToAnalyze, negativeControls, plottingDir, null);

        if (cachingResult != null) {
          waterAndMeohSpectra.add(cachingResult.getAnalysisResults());
        }
      } else {
        // This case is redundant for now but in the future, more media might be added, in which case, this conditional
        // will handle it.
        cachingResult = StandardIonResult.getForChemicalAndStandardWellAndNegativeWells(
            lcmsDir, db, chemical, wellToAnalyze, negativeControls, plottingDir, null);
      }

      if (cachingResult != null) {
        result.put(wellToAnalyze, cachingResult);
      }
    }

    return result;
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

    try (DB db = DB.openDBFromCLI(cl)) {
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);
      StandardIonAnalysis analysis = new StandardIonAnalysis();
      HashMap<Integer, Plate> plateCache = new HashMap<>();

      String plateBarcode = cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE);
      String inputChemicals = cl.getOptionValue(OPTION_STANDARD_CHEMICAL);
      String medium = cl.getOptionValue(OPTION_MEDIUM);

      // If standard chemical is specified, do standard LCMS ion selection analysis
      if (inputChemicals != null && !inputChemicals.equals("")) {
        String[] chemicals;
        if (!inputChemicals.contains(",")) {
          chemicals = new String[1];
          chemicals[0] = inputChemicals;
        } else {
          chemicals = inputChemicals.split(",");
        }

        String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + CSV_FORMAT;
        String plottingDirectory = cl.getOptionValue(OPTION_PLOTTING_DIR);
        String[] headerStrings = {"Molecule", "Plate Bar Code", "LCMS Detection Results"};
        CSVPrinter printer = new CSVPrinter(new FileWriter(outAnalysis), CSVFormat.DEFAULT.withHeader(headerStrings));

        for (String inputChemical : chemicals) {
          List<StandardWell> standardWells;

          Plate queryPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
          if (plateBarcode != null && medium != null) {
            standardWells = analysis.getStandardWellsForChemicalInSpecificPlateAndMedium(db, inputChemical,
                queryPlate.getId(), medium);
          } else if (plateBarcode != null) {
            standardWells = analysis.getStandardWellsForChemicalInSpecificPlate(db, inputChemical, queryPlate.getId());
          } else {
            standardWells = analysis.getStandardWellsForChemical(db, inputChemical);
          }

          if (standardWells.size() == 0) {
            throw new RuntimeException("Found no LCMS wells for " + inputChemical);
          }

          // Sort in descending order of media where MeOH and Water related media are promoted to the top and
          // anything derived from yeast media are demoted.
          Collections.sort(standardWells, new Comparator<StandardWell>() {
            @Override
            public int compare(StandardWell o1, StandardWell o2) {
              if (StandardWell.doesMediaContainYeastExtract(o1.getMedia()) &&
                  !StandardWell.doesMediaContainYeastExtract(o2.getMedia())) {
                return 1;
              } else {
                return 0;
              }
            }
          });

          Map<StandardWell, StandardIonResult> wellToIonRanking = StandardIonAnalysis.getBestMetlinIonsForChemical(
                  inputChemical, lcmsDir, db, standardWells, plottingDirectory);

          if (wellToIonRanking.size() != standardWells.size() && !cl.hasOption(OPTION_OVERRIDE_NO_SCAN_FILE_FOUND)) {
            throw new Exception("Could not find a scan file associated with one of the standard wells");
          }

          for (StandardWell well : wellToIonRanking.keySet()) {
            LinkedHashMap<String, XZ> snrResults = wellToIonRanking.get(well).getAnalysisResults();

            String snrRankingResults = "";
            int numResultsToShow = 0;

            Plate plateForWellToAnalyze = Plate.getPlateById(db, well.getPlateId());

            for (Map.Entry<String, XZ> ionToSnrAndTime : snrResults.entrySet()) {
              if (numResultsToShow > 3) {
                break;
              }

              String ion = ionToSnrAndTime.getKey();
              XZ snrAndTime = ionToSnrAndTime.getValue();

              snrRankingResults += String.format(ion + " (%.2f SNR at %.2fs); ", snrAndTime.getIntensity(),
                  snrAndTime.getTime());
              numResultsToShow++;
            }

            String[] resultSet = {inputChemical,
                plateForWellToAnalyze.getBarcode() + " " +
                    well.getCoordinatesString() + " " +
                    well.getMedia() + " " +
                    well.getConcentration(),
                snrRankingResults};

            printer.printRecord(resultSet);
          }
        }

        try {
          printer.flush();
          printer.close();
        } catch (IOException e) {
          System.err.println("Error while flushing/closing csv writer.");
          e.printStackTrace();
        }
      } else {
        // Get the set of chemicals that includes the construct and all it's intermediates
        Pair<ConstructEntry, List<ChemicalAssociatedWithPathway>> constructAndPathwayChems =
            analysis.getChemicalsForConstruct(db, cl.getOptionValue(OPTION_CONSTRUCT));
        System.out.format("Construct: %s\n", constructAndPathwayChems.getLeft().getCompositionId());

        for (ChemicalAssociatedWithPathway pathwayChem : constructAndPathwayChems.getRight()) {
          System.out.format("  Pathway chem %s\n", pathwayChem.getChemical());

          // Get all the standard wells for the pathway chemicals. These wells contain only the
          // the chemical added with controlled solutions (ie no organism or other chemicals in the
          // solution)

          List<StandardWell> standardWells;

          if (plateBarcode != null) {
            Plate queryPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
            standardWells = analysis.getStandardWellsForChemicalInSpecificPlate(db, pathwayChem.getChemical(), queryPlate.getId());
          } else {
            standardWells = analysis.getStandardWellsForChemical(db, pathwayChem.getChemical());
          }

          for (StandardWell wellToAnalyze : standardWells) {
            List<StandardWell> negativeControls = analysis.getViableNegativeControlsForStandardWell(db, wellToAnalyze);
            Map<StandardWell, List<ScanFile>> allViableScanFiles =
                analysis.getViableScanFilesForStandardWells(db, wellToAnalyze, negativeControls);

            List<String> primaryStandardScanFileNames = new ArrayList<>();
            for (ScanFile scanFile : allViableScanFiles.get(wellToAnalyze)) {
              primaryStandardScanFileNames.add(scanFile.getFilename());
            }
            Plate plate = plateCache.get(wellToAnalyze.getPlateId());
            if (plate == null) {
              plate = Plate.getPlateById(db, wellToAnalyze.getPlateId());
              plateCache.put(plate.getId(), plate);
            }

            System.out.format("    Standard well: %s @ %s, '%s'%s%s\n", plate.getBarcode(),
                wellToAnalyze.getCoordinatesString(),
                wellToAnalyze.getChemical(),
                wellToAnalyze.getMedia() == null ? "" : String.format(" in %s", wellToAnalyze.getMedia()),
                wellToAnalyze.getConcentration() == null ? "" : String.format(" @ %s", wellToAnalyze.getConcentration()));
            System.out.format("      Scan files: %s\n", StringUtils.join(primaryStandardScanFileNames, ", "));

            for (StandardWell negCtrlWell : negativeControls) {
              plate = plateCache.get(negCtrlWell.getPlateId());
              if (plate == null) {
                plate = Plate.getPlateById(db, negCtrlWell.getPlateId());
                plateCache.put(plate.getId(), plate);
              }
              List<String> negativeControlScanFileNames = new ArrayList<>();
              for (ScanFile scanFile : allViableScanFiles.get(negCtrlWell)) {
                negativeControlScanFileNames.add(scanFile.getFilename());
              }

              System.out.format("      Viable negative: %s @ %s, '%s'%s%s\n", plate.getBarcode(),
                  negCtrlWell.getCoordinatesString(),
                  negCtrlWell.getChemical(),
                  negCtrlWell.getMedia() == null ? "" : String.format(" in %s", negCtrlWell.getMedia()),
                  negCtrlWell.getConcentration() == null ? "" : String.format(" @ %s", negCtrlWell.getConcentration()));
              System.out.format("        Scan files: %s\n", StringUtils.join(negativeControlScanFileNames, ", "));
              // TODO: do something useful with the standard wells and their scan files, and then stop all the printing.
            }
          }
        }
      }
    }
  }
}
