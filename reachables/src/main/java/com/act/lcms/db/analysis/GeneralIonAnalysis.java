package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class GeneralIonAnalysis {
  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  public static final String CSV_FORMAT = "csv";
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_STANDARD_CHEMICAL = "sc";
  public static final String OPTION_OUTPUT_PREFIX = "o";
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
   *
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

  public <T extends PlateWell<T>> Pair<Map<String, String>, XZ> getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(
      File lcmsDir, DB db, T positiveWell, List<T> negativeWells, HashMap<Integer, Plate> plateCache, String chemical,
      String plottingDir) throws Exception {
    Plate plate = plateCache.get(positiveWell.getPlateId());

    if (plate == null) {
      plate = Plate.getPlateById(db, positiveWell.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    List<Pair<String, Double>> searchMZs;
    Pair<String, Double> searchMZ = Utils.extractMassFromString(db, chemical);
    if (searchMZ != null) {
      searchMZs = Collections.singletonList(searchMZ);
    } else {
      throw new RuntimeException("Could not find Mass Charge value for " + chemical);
    }

    List<T> posWells = new ArrayList<>();
    posWells.add(positiveWell);

    List<T> allWells = new ArrayList<>();
    allWells.addAll(posWells);

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataPos = AnalysisHelper.readWellScanData(
        db, lcmsDir, searchMZs, ScanData.KIND.POS_SAMPLE, plateCache, posWells, false, null, null,
        USE_SNR_FOR_LCMS_ANALYSIS, chemical);

    if (peakDataPos == null) {
      System.out.println("no positive data available");
    }

    List<ChemicalToMapOfMetlinIonsToIntensityTimeValues> negs = new ArrayList<>();
    List<Map<String, Map<String, List<XZ>>>> negsData = new ArrayList<>();

    for (T well : negativeWells) {
      List<T> negWell = new ArrayList<>();
      negWell.add(well);
      allWells.addAll(negWell);
      ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataNeg = AnalysisHelper.readWellScanData(
          db, lcmsDir, searchMZs, ScanData.KIND.NEG_CONTROL, plateCache, negWell, false, null, null,
          USE_SNR_FOR_LCMS_ANALYSIS, chemical);
      negsData.add(peakDataNeg.getPeakData());
      negs.add(peakDataNeg);
    }

    XZ snrResults = WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForNormalWells(peakDataPos, negs, chemical);

    Map<String, String> plottingFileMappings =
        ChemicalToMapOfMetlinIonsToIntensityTimeValues.plotPositiveAndNegativeControlsForEachMetlinIon3(searchMZ, allWells, peakDataPos.getPeakData(), negsData, plottingDir, chemical);

    return Pair.of(plottingFileMappings, snrResults);
  }

  /**
   * This function returns the best metlion ions of the SNR analysis for each well.
   *
   * @param chemical      - This is chemical of interest we are running ion analysis against.
   * @param lcmsDir       - The directory where the LCMS scan data can be found.
   * @param db
   * @param standardWells - The standard wells over which the analysis is done. This list is sorted in the following
   *                      order: wells in water or meoh are processed first, followed by everything else. This ordering
   *                      is required since the analysis on yeast media depends on the analysis of results in water/meoh.
   * @param plottingDir   - This is the directory where the plotting diagnostics will live.
   * @return A mapping of the well that was analyzed to the standard ion result.
   * @throws Exception
   */
  public static Map<StandardWell, StandardIonResult> getBestMetlinIonsForChemical(
      String chemical, File lcmsDir, DB db, List<StandardWell> standardWells, String plottingDir) throws Exception {

    Map<StandardWell, StandardIonResult> result = new HashMap<>();
    List<LinkedHashMap<String, XZ>> waterAndMeohSpectra = new ArrayList<>();

    for (StandardWell wellToAnalyze : standardWells) {
      List<StandardWell> negativeControls =
          GeneralIonAnalysis.getViableNegativeControlsForStandardWell(db, wellToAnalyze);

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

    GeneralIonAnalysis ga = new GeneralIonAnalysis();

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

      HashMap<Integer, Plate> plateCache = new HashMap<>();

      String inputChemicalsFile = cl.getOptionValue(OPTION_STANDARD_CHEMICAL);

      List<String> inputChemicals = new ArrayList<>();

      BufferedReader br = new BufferedReader(new FileReader(new File(inputChemicalsFile)));

      String line = null;

      while ((line = br.readLine()) != null) {
        inputChemicals.add(MassCalculator.calculateMass(line).toString());
      }

      Map<String, Pair<Integer, Integer>> barcodeToCoordinates = new HashMap<>();

//      //pa1 supe, TA, out1
      barcodeToCoordinates.put("13872", Pair.of(2, 0));
////
////      //pa1 supe, TB, out2
      barcodeToCoordinates.put("13872.", Pair.of(3, 0));

////      //pa2 supe, TA, out3
//      barcodeToCoordinates.put("8140", Pair.of(2, 5));
////
////      //pa2 supe, TB, out4
//      barcodeToCoordinates.put("8142", Pair.of(2, 5))

      Integer counter = 0;

      Plate queryPlate = Plate.getPlateByBarcode(db, "13499");
      LCMSWell negativeWell1 = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate.getId(), 4, 0);
      LCMSWell negativeWell2 = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate.getId(), 6, 0);

      Plate queryPlate3 = Plate.getPlateByBarcode(db, "13873");
      LCMSWell negativeWell3 = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate3.getId(), 0, 10);
      LCMSWell negativeWell4 = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate3.getId(), 0, 4);

      List<LCMSWell> negativeWells = new ArrayList<>();
      negativeWells.add(negativeWell1);
      negativeWells.add(negativeWell2);
      negativeWells.add(negativeWell3);
      negativeWells.add(negativeWell4);

      for (Map.Entry<String, Pair<Integer, Integer>> entry : barcodeToCoordinates.entrySet()) {
        String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + counter.toString() + "." + CSV_FORMAT;
        String plottingDirectory = cl.getOptionValue(OPTION_PLOTTING_DIR);
        String[] headerStrings = {"Chemical", "Positive Sample", "Negative Sample1", "Negative Sample2", "SNR", "Time", "Plots"};
        CSVPrinter printer = new CSVPrinter(new FileWriter(outAnalysis), CSVFormat.DEFAULT.withHeader(headerStrings));

        String key = entry.getKey().replace(".", "");

        Plate queryPlate1 = Plate.getPlateByBarcode(db, key);
        LCMSWell positiveWell = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate1.getId(), entry.getValue().getLeft(), entry.getValue().getRight());

        for (String inputChemical : inputChemicals) {
          Pair<Map<String, String>, XZ> val =
              ga.getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(lcmsDir, db, positiveWell, negativeWells, plateCache, inputChemical, plottingDirectory);

          String[] resultSet = {
              inputChemical,
              positiveWell.getMsid(),
              negativeWell1.getMsid(),
              negativeWell2.getMsid(),
              val.getRight().getIntensity().toString(),
              val.getRight().getTime().toString(),
              val.getLeft().get("M+H")
          };

          printer.printRecord(resultSet);
        }

        try {
          printer.flush();
          printer.close();
        } catch (IOException e) {
          System.err.println("Error while flushing/closing csv writer.");
          e.printStackTrace();
        }
        counter++;
      }
    }
  }
}