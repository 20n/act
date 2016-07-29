package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class IonDetectionAnalysis {

  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  private static final boolean USE_FINE_GRAINED_TOLERANCE = false;
  private static final String DEFAULT_ION = "M+H";
  private static final String CSV_FORMAT = "tsv";
  private static final Double MIN_INTENSITY_THRESHOLD = 10000.0;
  private static final Double MIN_SNR_THRESHOLD = 1000.0;
  private static final Double MIN_TIME_THRESHOLD = 15.0;
  private static final String OPTION_LCMS_FILE_DIRECTORY = "d";
  private static final String OPTION_INPUT_PREDICTION_CORPUS = "sc";
  private static final String OPTION_OUTPUT_PREFIX = "o";
  private static final String OPTION_PLOTTING_DIR = "p";
  private static final String OPTION_INCLUDE_IONS = "i";
  private static final String OPTION_EXCLUDE_IONS = "x";
  private static final String OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE = "t";
  private static final String HEADER_WELL_TYPE = "WELL_TYPE";
  private static final String HEADER_WELL_ROW = "WELL_ROW";
  private static final String HEADER_WELL_COLUMN = "WELL_COLUMN";
  private static final String HEADER_PLATE_BARCODE = "PLATE_BARCODE";
  private static final Set<String> ALL_HEADERS =
      new HashSet<>(Arrays.asList(HEADER_WELL_TYPE, HEADER_WELL_ROW, HEADER_WELL_COLUMN, HEADER_PLATE_BARCODE));

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write a help message."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_LCMS_FILE_DIRECTORY)
        .argName("directory")
        .desc("The directory where LCMS analysis results live")
        .hasArg().required()
        .longOpt("data-dir")
    );
    // The OPTION_INPUT_PREDICTION_CORPUS file is a json formatted file that is serialized from the class "L2PredictionCorpus"
    add(Option.builder(OPTION_INPUT_PREDICTION_CORPUS)
        .argName("input prediction corpus")
        .desc("The input prediction corpus")
        .hasArg().required()
        .longOpt("prediction-corpus")
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
    add(Option.builder(OPTION_INCLUDE_IONS)
        .argName("ion list")
        .desc("A comma-separated list of ions to include in the search (ions not in this list will be ignored)")
        .hasArgs().valueSeparator(',')
        .longOpt("include-ions")
    );
    add(Option.builder(OPTION_EXCLUDE_IONS)
        .argName("ion list")
        .desc("A comma-separated list of ions to exclude from the search, takes precedence over include-ions")
        .hasArgs().valueSeparator(',')
        .longOpt("exclude-ions")
    );
    // This input file is structured as a tsv file with the following schema:
    //    WELL_TYPE  PLATE_BARCODE  WELL_ROW  WELL_COLUMN
    // eg.   POS        12389        0           1
    add(Option.builder(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE)
        .argName("input positive and negative control wells")
        .desc("A tsv file containing positive and negative wells")
        .hasArg().required()
        .longOpt("input-positive-negative-control-wells")
    );
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public static <T extends PlateWell<T>> Map<String, Pair<String, Pair<XZ, Double>>> getSnrResultsAndPlotDiagnosticsForEachMoleculeAndItsMetlinIon(
      File lcmsDir, DB db, T positiveWell, List<T> negativeWells, HashMap<Integer, Plate> plateCache, List<Pair<String, Double>> searchMZs,
      String plottingDir) throws Exception {

    Plate plate = plateCache.get(positiveWell.getPlateId());
    if (plate == null) {
      plate = Plate.getPlateById(db, positiveWell.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    ChemicalToMapOfMetlinIonsToIntensityTimeValues positiveWellSignalProfiles = AnalysisHelper.readScanData(
        db,
        lcmsDir,
        searchMZs,
        ScanData.KIND.POS_SAMPLE,
        plateCache,
        positiveWell,
        USE_FINE_GRAINED_TOLERANCE,
        null,
        null,
        USE_SNR_FOR_LCMS_ANALYSIS);

    if (positiveWellSignalProfiles == null) {
      System.err.println("no positive data available");
      System.exit(1);
    }

    List<ChemicalToMapOfMetlinIonsToIntensityTimeValues> negativeWellsSignalProfiles = new ArrayList<>();
    List<T> allWells = new ArrayList<>();
    allWells.add(positiveWell);
    allWells.addAll(negativeWells);

    for (T well : negativeWells) {
      ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataNeg = AnalysisHelper.readScanData(
          db,
          lcmsDir,
          searchMZs,
          ScanData.KIND.NEG_CONTROL,
          plateCache,
          well,
          USE_FINE_GRAINED_TOLERANCE,
          null,
          null,
          USE_SNR_FOR_LCMS_ANALYSIS);

      negativeWellsSignalProfiles.add(peakDataNeg);
    }

    Map<String, Pair<XZ, Double>> snrResults =
        WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForNormalWells(
            positiveWellSignalProfiles, negativeWellsSignalProfiles, searchMZs, MIN_INTENSITY_THRESHOLD);

    Map<String, String> plottingFileMappings =
        ChemicalToMapOfMetlinIonsToIntensityTimeValues.plotPositiveAndNegativeControlsForEachMZ(
            searchMZs, allWells, positiveWellSignalProfiles, negativeWellsSignalProfiles, plottingDir);

    Map<String, Pair<String, Pair<XZ, Double>>> mzToPlotDirAndSNR = new HashMap<>();
    for (Map.Entry<String, Pair<XZ, Double>> entry : snrResults.entrySet()) {
      String plottingPath = plottingFileMappings.get(entry.getKey());
      XZ snr = entry.getValue().getLeft();

      if (plottingDir == null || snr == null) {
        System.err.format("Plotting directory or snr is null\n");
        System.exit(1);
      }

      mzToPlotDirAndSNR.put(entry.getKey(), Pair.of(plottingPath, entry.getValue()));
    }

    return mzToPlotDirAndSNR;
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

    File lcmsDir = new File(cl.getOptionValue(OPTION_LCMS_FILE_DIRECTORY));
    if (!lcmsDir.isDirectory()) {
      System.err.format("File at %s is not a directory\n", lcmsDir.getAbsolutePath());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    String plottingDirectory = cl.getOptionValue(OPTION_PLOTTING_DIR);

    // Get include and excluse ions from command line
    Set<String> includeIons;
    if (cl.hasOption(OPTION_INCLUDE_IONS)) {
      String[] ionNames = cl.getOptionValues(OPTION_INCLUDE_IONS);
      includeIons = new HashSet<>(Arrays.asList(ionNames));
      System.out.format("Including ions in search: %s\n", StringUtils.join(includeIons, ", "));
    } else {
      includeIons = new HashSet<>();
      includeIons.add(DEFAULT_ION);
    }

    Set<String> excludeIons = null;
    if (cl.hasOption(OPTION_EXCLUDE_IONS)) {
      String[] ionNames = cl.getOptionValues(OPTION_EXCLUDE_IONS);
      excludeIons = new HashSet<>(Arrays.asList(ionNames));
      System.out.format("Excluding ions from search: %s\n", StringUtils.join(excludeIons, ", "));
    }

    // Read product inchis from the prediction corpus
    File inputPredictionCorpus = new File(cl.getOptionValue(OPTION_INPUT_PREDICTION_CORPUS));
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputPredictionCorpus);

    List<Pair<String, Double>> searchMZs = new ArrayList<>();
    Map<Double, List<Pair<String, String>>> massChargeToChemicalAndString = new HashMap<>();
    Map<Double, List<Integer>> massChargeToListOfCorpusIds = new HashMap<>();

    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      for (String product : prediction.getProductInchis()) {
        // Assume the ion modes are all positive!
        Map<String, Double> allMasses = MS1.getIonMasses(MassCalculator.calculateMass(product), MS1.IonMode.POS);
        Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, includeIons, excludeIons);
        for (Map.Entry<String, Double> entry : metlinMasses.entrySet()) {
          List<Pair<String, String>> res = massChargeToChemicalAndString.get(entry.getValue());
          if (res == null) {
            res = new ArrayList<>();
            massChargeToChemicalAndString.put(entry.getValue(), res);
          }
          res.add(Pair.of(product, entry.getKey()));

          List<Integer> corpusIds = massChargeToListOfCorpusIds.get(entry.getValue());
          if (corpusIds == null) {
            corpusIds = new ArrayList<>();
            massChargeToListOfCorpusIds.put(entry.getValue(), corpusIds);
          }
          corpusIds.add(prediction.getId());
        }
      }
    }

    Integer chemicalCounter = 0;
    Map<String, Double> chemIDToMassCharge = new HashMap<>();
    for (Double massCharge : massChargeToChemicalAndString.keySet()) {
      String chemID = "CHEM_" + chemicalCounter.toString();
      chemIDToMassCharge.put(chemID, massCharge);
      searchMZs.add(Pair.of(chemID, massCharge));
      chemicalCounter++;
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      //ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      // Get experimental setup ie. positive and negative wells from config file
      List<LCMSWell> positiveWells = new ArrayList<>();
      List<LCMSWell> negativeWells = new ArrayList<>();

      TSVParser parser = new TSVParser();
      parser.parse(new File(cl.getOptionValue(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE)));
      Set<String> headerSet = new HashSet<>(parser.getHeader());

      if (!headerSet.equals(ALL_HEADERS)) {
        System.err.format("Invalid header type");
        System.exit(1);
      }

      for (Map<String, String> row : parser.getResults()) {
        String wellType = row.get(HEADER_WELL_TYPE);
        String barcode = row.get(HEADER_PLATE_BARCODE);
        Integer rowCoordinate = Integer.parseInt(row.get(HEADER_WELL_ROW));
        Integer columnCoordinate = Integer.parseInt(row.get(HEADER_WELL_COLUMN));
        Plate queryPlate = Plate.getPlateByBarcode(db, barcode);
        LCMSWell well = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate.getId(), rowCoordinate, columnCoordinate);
        if (wellType.equals("POS")) {
          positiveWells.add(well);
        } else {
          negativeWells.add(well);
        }
      }

      HashMap<Integer, Plate> plateCache = new HashMap<>();
      String outputPrefix = cl.getOptionValue(OPTION_OUTPUT_PREFIX);

      List<List<String[]>> resultComparisons = new ArrayList<>(positiveWells.size());

      String[] headerStrings = {"Mass Charge", "Inchis", "Ion Info", "Positive Sample ID", "SNR", "Time", "Plots"};

      for (LCMSWell positiveWell : positiveWells) {
        String outAnalysis = outputPrefix + "_" + positiveWell.getId().toString() + "." + CSV_FORMAT;
        TSVWriter printer = new TSVWriter(Arrays.asList(headerStrings));
        printer.open(new File(outAnalysis));

        Map<String, Pair<String, Pair<XZ, Double>>> result =
            getSnrResultsAndPlotDiagnosticsForEachMoleculeAndItsMetlinIon(
                lcmsDir,
                db,
                positiveWell,
                negativeWells,
                plateCache,
                searchMZs,
                plottingDirectory);

        int counter = 0;
        for (Map.Entry<String, Pair<String, Pair<XZ, Double>>> mzToPlotAndSnr : result.entrySet()) {

          Double massCharge = chemIDToMassCharge.get(mzToPlotAndSnr.getKey());
          List<Pair<String, String>> inchisAndIon = massChargeToChemicalAndString.get(massCharge);
          StringBuilder inchiBuilder = new StringBuilder();
          StringBuilder inchiIonBuilder = new StringBuilder();
          for (Pair<String, String> pair : inchisAndIon) {
            inchiBuilder.append(pair.getLeft());
            inchiBuilder.append("|");
            inchiIonBuilder.append(pair.getRight());
            inchiIonBuilder.append("|");
          }

          Map<String, String> resultSet2 = new HashMap<>();
          resultSet2.put("Mass Charge", massCharge.toString());
          resultSet2.put("Inchis", inchiBuilder.toString());
          resultSet2.put("Ion Info", inchiIonBuilder.toString());
          resultSet2.put("Positive Sample ID", positiveWell.getMsid());
          resultSet2.put("SNR", mzToPlotAndSnr.getValue().getRight().getLeft().getIntensity().toString());
          resultSet2.put("Time", mzToPlotAndSnr.getValue().getRight().getLeft().getTime().toString());
          resultSet2.put("Plots", mzToPlotAndSnr.getValue().getLeft());

          String[] resultSet = {
              massCharge.toString(),
              inchiBuilder.toString(),
              inchiIonBuilder.toString(),
              positiveWell.getMsid(),
              mzToPlotAndSnr.getValue().getRight().getLeft().getIntensity().toString(),
              mzToPlotAndSnr.getValue().getRight().getLeft().getTime().toString(),
              mzToPlotAndSnr.getValue().getLeft()
          };

          if (resultComparisons.size() > result.entrySet().size()) {
            resultComparisons.get(counter).add(resultSet);
          } else {
            List<String[]> analysisRow = new ArrayList<>();
            analysisRow.add(resultSet);
            resultComparisons.add(analysisRow);
          }

          printer.append(resultSet2);
          printer.flush();
          counter++;
        }

        try {
          printer.flush();
          printer.close();
        } catch (IOException e) {
          System.err.println("Error while flushing/closing csv writer.");
          e.printStackTrace();
        }
      }

      // Post process analysis
      String outAnalysis = outputPrefix + "_post_process" + "." + CSV_FORMAT;

      String[] headerStringsFinal = {"Inchis", "CorpusIds", "Plots"};

      TSVWriter postAnalysisPrinter = new TSVWriter(Arrays.asList(headerStringsFinal));
      postAnalysisPrinter.open(new File(outAnalysis));

      for (List<String[]> listOfComparisons : resultComparisons) {
        Boolean failure = false;
        StringBuilder inchis = new StringBuilder();
        StringBuilder plots = new StringBuilder();

        Set<Integer> allIds = new HashSet<>();

        for (String[] result : listOfComparisons) {

          Double massCharge = Double.parseDouble(result[0]);
          List<Integer> corpusIds = massChargeToListOfCorpusIds.get(massCharge);
          allIds.addAll(corpusIds);

          inchis.append(result[1]);
          inchis.append("|");

          plots.append(result[6]);
          plots.append(",");

          if (Double.parseDouble(result[4]) < MIN_SNR_THRESHOLD || Double.parseDouble(result[5]) < MIN_TIME_THRESHOLD) {
            failure = true;
            break;
          }
        }

        if (!failure) {
          Map<String, String> res = new HashMap<>();
          res.put("Inchis", inchis.toString());
          res.put("Plots", plots.toString());

          StringBuilder ids = new StringBuilder();
          for (Integer id : allIds) {
            ids.append(id.toString());
            ids.append(",");
          }

          res.put("CorpusIds", ids.toString());

          postAnalysisPrinter.append(res);
          postAnalysisPrinter.flush();
        }

      }

      postAnalysisPrinter.close();
    }
  }
}