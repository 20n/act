package com.act.lcms.db.analysis;

import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.ConstructEntry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public class StandardIonAnalysis {
  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  private static final String TEXT_FORMAT = "txt";
  private static final String PDF_FORMAT = "pdf";
  private static final String DATA_FORMAT = "data";
  private static final String CSV_FORMAT = "csv";
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_STANDARD_CHEMICAL = "sc";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_MEDIUM = "m";

  //Delimiter used in CSV file
  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

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
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
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
   * Find all standard wells containing a specified chemical that is associated with a construct's pathway.
   * @param db The DB connection to query.
   * @param pathwayChem The chemical for which to find standard wells.
   * @return A list of standard wells (in any plate) containing the specified chemical.
   * @throws SQLException
   */
  public List<StandardWell> getStandardWellsForChemical(DB db, String pathwayChem)
      throws SQLException {
    return StandardWell.getInstance().getStandardWellsByChemical(db, pathwayChem);
  }

  /**
   * Find all standard wells containing a specified chemical that is associated with a construct's pathway.
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
  public List<StandardWell> getViableNegativeControlsForStandardWell(DB db, StandardWell baseStandard)
      throws SQLException {
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

        FileWriter fileWriter = new FileWriter(outAnalysis);
        fileWriter.append("Molecule, Plate Bar Code, LCMS Detection Results");
        fileWriter.append(NEW_LINE_SEPARATOR);

        String[] headerStrings = new String[3];
        headerStrings[0] = "Molecule";
        headerStrings[1] = "Plate Bar Code";
        headerStrings[2] = "LCMS Detection Results";

        CSVPrinter printer = new CSVPrinter(new FileWriter(outAnalysis), CSVFormat.DEFAULT.withHeader(headerStrings));

        for (String inputChemical : chemicals) {
          List<StandardWell> standardWells;
          List<StandardWell> standardWellsToAnalyze = new ArrayList<>();

          if (plateBarcode != null) {
            Plate queryPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
            standardWells = analysis.getStandardWellsForChemicalInSpecificPlate(db, inputChemical, queryPlate.getId());
          } else {
            standardWells = analysis.getStandardWellsForChemical(db, inputChemical);
          }

          if (standardWells.size() == 0) {
            throw new RuntimeException("Found no LCMS wells for " + inputChemical);
          }

          // TODO: We currently just select the first standard well to analyze. We could be more clever here
          // when we have multiple standard wells, maybe pick the one with a good medium like water.
          // TODO: Have an command line option of medium preference
          // TODO: Add a function to this file to get wells from a selected medium.
          String medium = cl.getOptionValue(OPTION_MEDIUM);
          if (medium != null) {
            for (StandardWell well : standardWells) {
              if (well.getMedia().equals(medium)) {
                standardWellsToAnalyze.add(well);
              }
            }

            if (standardWellsToAnalyze.size() == 0) {
              throw new RuntimeException("Found no wells with the medium " + medium);
            }
          } else {
            standardWellsToAnalyze = standardWells;
          }

          for (StandardWell wellToAnalyze : standardWellsToAnalyze) {
            List<StandardWell> negativeControls = analysis.getViableNegativeControlsForStandardWell(db, wellToAnalyze);
            Plate plate = plateCache.get(wellToAnalyze.getPlateId());
            if (plate == null) {
              plate = Plate.getPlateById(db, wellToAnalyze.getPlateId());
              plateCache.put(plate.getId(), plate);
            }

            List<Pair<String, Double>> searchMZs;
            Pair<String, Double> searchMZ = Utils.extractMassFromString(db, inputChemical);
            if (searchMZ != null) {
              searchMZs = Collections.singletonList(searchMZ);
            } else {
              throw new RuntimeException("Could not find Mass Charge value for " + inputChemical);
            }

            List<StandardWell> allWells = new ArrayList<>();
            allWells.add(wellToAnalyze);
            allWells.addAll(negativeControls);

            Plate plateForWellToAnalyze = Plate.getPlateById(db, wellToAnalyze.getPlateId());

            ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = AnalysisHelper.readScanData(
                db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, allWells, false, null, null,
                USE_SNR_FOR_LCMS_ANALYSIS);

            Set<Map.Entry<String, Pair<Double, Double>>> snrResults =
                WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNR(peakData, inputChemical);

            String snrRankingResults = "";
            int numResultsToShow = 0;

            for (Map.Entry<String, Pair<Double, Double>> ionToSnrAndTime : snrResults) {
              if (numResultsToShow > 3) {
                break;
              }

              String ion = ionToSnrAndTime.getKey();
              Pair<Double, Double> snrAndTime = ionToSnrAndTime.getValue();

              snrRankingResults += String.format(ion + " (%.2f SNR at %.2fs)", snrAndTime.getLeft(),
                  snrAndTime.getRight());
              snrRankingResults += "; ";
              numResultsToShow++;
            }

            //Print results in output file
            fileWriter.append(inputChemical);
            fileWriter.append(COMMA_DELIMITER);
            fileWriter.append(plateForWellToAnalyze.getBarcode() + " " + wellToAnalyze.getCoordinatesString() + " " + wellToAnalyze.getMedia() + " " + wellToAnalyze.getConcentration());
            fileWriter.append(COMMA_DELIMITER);
            fileWriter.append(snrRankingResults);
            fileWriter.append(NEW_LINE_SEPARATOR);

//            String[] resultSet = new String[3];
//            resultSet[0] = inputChemical;
//            headerStrings[1] = plateForWellToAnalyze.getBarcode() + " " + wellToAnalyze.getCoordinatesString() + " " + wellToAnalyze.getMedia() + " " + wellToAnalyze.getConcentration();
//            headerStrings[2] = snrRankingResults;
//            printer.printRecord(resultSet);
          }
        }

        try {
          fileWriter.flush();
          fileWriter.close();
          printer.flush();
          printer.close();
        } catch (IOException e) {
          System.err.println("Error while flushing/closing fileWriter.");
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
