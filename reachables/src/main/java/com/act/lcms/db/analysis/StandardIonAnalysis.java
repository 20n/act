package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.XZ;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public class StandardIonAnalysis {
  private final int DEFAULT_STANDARD_WELL_INDEX = 0;
  private final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  private final int PEAK_DETECTION_DENOMINATION = 1000;
  private final String OVERLAP_DECISION_YES = "YES";
  private final String OVERLAP_DECISION_NO = "NO";
  private final double TIME_TOLERANCE_IN_SECONDS = 2.0;
  private final String DATA_ANALYSIS_OUTPUT_FILE = "lcms_data_analysis";
  private final String TEXT_FORMAT = "txt";
  private final String PDF_FORMAT = "pdf";
  private final String DATA_FORMAT = "data";

  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_STANDARD_PLATE_BARCODE = "sp";
  public static final String OPTION_STANDARD_CHEMICAL = "sc";
  public static final String OPTION_OUTPUT_PREFIX = "o";

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
      Plate queryPlate = Plate.getPlateByBarcode(db, cl.getOptionValue(OPTION_STANDARD_PLATE_BARCODE));
      String inputChemical = cl.getOptionValue(OPTION_STANDARD_CHEMICAL);

      // If standard chemical is specified, do standard LCMS ion selection analysis
      if (inputChemical != null || !inputChemical.equals("")) {

        List<StandardWell> standardWells;
        if (queryPlate != null) {
          standardWells = analysis.getStandardWellsForChemicalInSpecificPlate(db, inputChemical, queryPlate.getId());
        } else {
          standardWells = analysis.getStandardWellsForChemical(db, inputChemical);
        }

        if (standardWells.size() == 0) {
          throw new RuntimeException("Found no LCMS wells for the specified chemical");
        }

        // TODO: We currently just select the first standard well to analyze. We could be more clever here
        // when we have multiple standard wells, maybe pick the one with a good medium like water.
        // TODO: Have an command line option of medium preference maybe?
        StandardWell wellToAnalyze = standardWells.get(DEFAULT_STANDARD_WELL_INDEX);
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

        List<Pair<String, Double>> searchMZs = null;
        Pair<String, Double> searchMZ = Utils.extractMassFromString(db, inputChemical);
        if (searchMZ != null) {
          searchMZs = Collections.singletonList(searchMZ);
        } else {
          throw new RuntimeException("Could not find Mass Charge value for the chemical.");
        }

        List<StandardWell> standardWellsSubset = new ArrayList<>();
        standardWellsSubset.add(wellToAnalyze);

        Pair<List<ScanData<StandardWell>>, Double> allStandardScans =
                AnalysisHelper.processScans(
                    db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, standardWellsSubset, false, null, null,
                    USE_SNR_FOR_LCMS_ANALYSIS);

        Pair<List<ScanData<StandardWell>>, Double> allNegativeScans =
                AnalysisHelper.processScans(
                    db, lcmsDir, searchMZs, ScanData.KIND.STANDARD, plateCache, negativeControls, false, null, null,
                    USE_SNR_FOR_LCMS_ANALYSIS);

        List<ScanData> allScanData = new ArrayList<ScanData>() {{
          addAll(allStandardScans.getLeft());
          addAll(allNegativeScans.getLeft());
        }};

        if (allScanData.size() == 0) {
          throw new RuntimeException("Could not find any scan data for wells.");
        }

        // we use the maxIntensity for a threshold value in our peak detection algorithm.
        Double maxIntensity = Math.max(allStandardScans.getRight(), allNegativeScans.getRight());

        try (FileOutputStream fos = new FileOutputStream("outData")) {
          Map<String, Map<String, List<Pair<Double, Double>>>> peakData = new HashMap<>();
          Integer iter = 0;

          for (ScanData scanData: allScanData) {
            //Standard positive ingestion
            Map<String, List<XZ>> scanResult = AnalysisHelper.readScanData(lcmsDir, scanData, false, true);

            // read intensity and time data for each metlin mass
            for (Map.Entry<String, List<XZ>> ms1ForIon : scanResult.entrySet()) {
              String ion = ms1ForIon.getKey();
              List<XZ> ms1 = ms1ForIon.getValue();
              ArrayList<Pair<Double, Double>> intensityAndTimeValues = new ArrayList<>();

              for (XZ xz : ms1) {
                Pair<Double, Double> value = new ImmutablePair<>(xz.getIntensity(), xz.getTime());
                intensityAndTimeValues.add(value);
              }

              // We used a threshold value of maxIntensity / 1000 here since this was observed to provide a reasonable
              // number of potential peaks (in the order of 10s) to do further analysis with.
              List<Pair<Double, Double>> peaksOfIntensityAndTimeForMetlinIon =
                  WaveformAnalysis.detectPeaksInIntensityTimeWaveform(
                      intensityAndTimeValues,
                      maxIntensity / PEAK_DETECTION_DENOMINATION);

              String standardOrNegativeControl;

              //Since we know that the first value in allScanData is the positive standard, make sure to use that.
              if (iter == 0) {
                standardOrNegativeControl = ScanData.KIND.STANDARD.toString();
              } else {
                standardOrNegativeControl = ScanData.KIND.NEG_CONTROL.toString() + iter.toString();
              }

              // peakData is organized as follows: STANDARD -> Metlin Ion #1 -> (A bunch of peaks)
              //                                   NEG_CONTROL1 -> Metlin Ion #1 -> (A bunch of peaks) etc.
              Map<String, List<Pair<Double, Double>>> val = peakData.get(standardOrNegativeControl);
              if (val != null) {
                val.put(ion, peaksOfIntensityAndTimeForMetlinIon);
              } else {
                val = new HashMap<>();
                val.put(ion, peaksOfIntensityAndTimeForMetlinIon);
              }
              peakData.put(standardOrNegativeControl, val);
            }

            iter++;
          }

          // PART 1: Rank order all the metlin ions from the positive standard scan. We do this by looking at the
          // highest peak by intensity.
          Map<String, Double> ionToHighestPeak = new HashMap<>();
          for (Map.Entry<String, List<Pair<Double, Double>>> metlinMassResult :
              peakData.get(ScanData.KIND.STANDARD.toString()).entrySet()) {
            List<Pair<Double, Double>> peaks = metlinMassResult.getValue();
            if (peaks.size() > 0) {
              // Each key in peakData is sorted in ascending order of intensity, based on the sort order of the return value
              // of WaveformAnalysis.detectPeaksInIntensityTimeWaveform, therefore the last value is the highest peak.
              Integer highestPeakIndex = peaks.size() - 1;
              ionToHighestPeak.put(metlinMassResult.getKey(), peaks.get(highestPeakIndex).getLeft());
            }
          }

          // Convert Map to List
          List<Map.Entry<String, Double>> ionToHighestPeakList = new LinkedList<>(ionToHighestPeak.entrySet());

          // Sort list with comparator, to compare the Map values
          Collections.sort(ionToHighestPeakList, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
              return (o1.getValue()).compareTo(o2.getValue());
            }
          });

          // sortedMetlinIonsToHighestPeakMap stores the highest peaks of each metlin ion corresponding to the positive
          // standard in ascending order. That is, the metlin ion with the highest intensity is in the last index.
          Map<String, Double> sortedMetlinIonsToHighestPeakMap = new LinkedHashMap<>();
          for (Map.Entry<String, Double> ionToIntensity : ionToHighestPeakList) {
            sortedMetlinIonsToHighestPeakMap.put(ionToIntensity.getKey(), ionToIntensity.getValue());
          }

          // PART 2: Do cross comparisons between positive standard and negative controls.
          Map<String, String> negativeControlComparisonResult = new HashMap<>();
          //For every metlin mass of the positive ion, compare it against the negative scan's ion
          for (Map.Entry<String, List<Pair<Double, Double>>> metlinMassResult :
              peakData.get(ScanData.KIND.STANDARD.toString()).entrySet()) {
            String ion = metlinMassResult.getKey();
            List<Pair<Double, Double>> standardData = metlinMassResult.getValue();

            for (Map.Entry<String, Map<String, List<Pair<Double, Double>>>> sampleToMetlinIonData : peakData.entrySet()) {
              // Iterate through all the negative scans and find the metlin ion corresponding to the given ion in the
              // positive standard ion.
              if (!sampleToMetlinIonData.getKey().equals(ScanData.KIND.STANDARD.toString())) {
                Map<String,List<Pair<Double,Double>>> mapping = sampleToMetlinIonData.getValue();
                List<Pair<Double, Double>> negativeControlData = mapping.get(ion);

                if (negativeControlData != null) {
                  //compare cross chart
                  if (mapping.get(ion).size() > 0) {
                    //see if the standard's time is in the vicinity of the neg control's
                    if (WaveformAnalysis.doPeaksOverlap(standardData, negativeControlData, TIME_TOLERANCE_IN_SECONDS)) {
                      negativeControlComparisonResult.put(ion, OVERLAP_DECISION_YES);
                    } else {
                      negativeControlComparisonResult.put(ion, OVERLAP_DECISION_NO);
                    }
                  } else {
                    negativeControlComparisonResult.put(ion, OVERLAP_DECISION_NO);
                  }
                } else {
                  negativeControlComparisonResult.put(ion, OVERLAP_DECISION_NO);
                }
              }
            }
          }

          //PART 3: Print results in output file
          String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + TEXT_FORMAT;
          PrintWriter writer = new PrintWriter(outAnalysis, "UTF-8");
          Iterator dataIterator = sortedMetlinIonsToHighestPeakMap.entrySet().iterator();
          Integer ranking = sortedMetlinIonsToHighestPeakMap.size();
          writer.println(inputChemical);
          writer.format(Locale.US, "%20s %20s %20s %20s \r\n", "Metlin Ion", "Ranking", "Chart location", "Overlaps with Negative control?");

          for (Map.Entry<String, Double> ionToIntensity : sortedMetlinIonsToHighestPeakMap.entrySet()) {
            String ion = ionToIntensity.getKey();
            String overlapResult = negativeControlComparisonResult.get(ion);

            List<Pair<Double, Double>> peaksOfIon = peakData.get(ScanData.KIND.STANDARD.toString()).get(ion);
            DecimalFormat df = new DecimalFormat("#.##");

            Pair<Double, Double> lastPeaksOfIon = peaksOfIon.get(peaksOfIon.size() - 1);
            String location = String.format("%.2f intensity at %.2fs", lastPeaksOfIon.getLeft(), lastPeaksOfIon.getRight());

            writer.format(Locale.US, "%20s %20s %20s %20s \r\n", ion, ranking.toString(), location, overlapResult);
            ranking--;
          }

          writer.close();

          //print plots for cross-referencing
          List<String> graphLabels = new ArrayList<>();
          for (ScanData scanData : allScanData) {
            graphLabels.addAll(
                    AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, scanData, false, false,
                            true, true));
          }

          String outImg = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + PDF_FORMAT;
          String outData = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + DATA_FORMAT;
          Gnuplotter plotter = new Gnuplotter();
          plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time",
                  maxIntensity, "intensity", PDF_FORMAT);
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
          if (queryPlate == null) {
            standardWells = analysis.getStandardWellsForChemical(db, pathwayChem.getChemical());
          } else {
            standardWells = analysis.getStandardWellsForChemicalInSpecificPlate(db, pathwayChem.getChemical(), queryPlate.getId());
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
