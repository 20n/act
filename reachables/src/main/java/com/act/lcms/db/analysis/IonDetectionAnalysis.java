package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.MassCalculator;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
import com.act.utils.TSVParser;
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
  private static final String CSV_FORMAT = "csv";
  private static final String OPTION_LCMS_FILE_DIRECTORY = "d";
  private static final String OPTION_INPUT_PREDICTION_CORPUS = "sc";
  private static final String OPTION_OUTPUT_PREFIX = "o";
  private static final String OPTION_PLOTTING_DIR = "p";
  private static final String OPTION_INCLUDE_IONS = "i";
  private static final String OPTION_EXCLUDE_IONS = "x";
  private static final String OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE = "t";
  private static final String HEADER_WELL_TYPE = "WELL_TYPE";
  private static final String HEADER_WELL_ROW = "ROW";
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

  public static <T extends PlateWell<T>> Map<MoleculeAndItsMetlinIon, Pair<String, XZ>> getSnrResultsAndPlotDiagnosticsForEachMoleculeAndItsMetlinIon(
      File lcmsDir, DB db, T positiveWell, List<T> negativeWells, HashMap<Integer, Plate> plateCache, List<String> chemicals,
      String plottingDir, Set<String> includeIons, Set<String> excludeIons) throws Exception {

    Plate plate = plateCache.get(positiveWell.getPlateId());
    if (plate == null) {
      plate = Plate.getPlateById(db, positiveWell.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    List<Pair<String, Double>> searchMZs = new ArrayList<>();
    for (String chemical : chemicals) {
      Pair<String, Double> searchMZ = Utils.extractMassFromString(db, chemical);
      if (searchMZ != null) {
        searchMZs.add(searchMZ);
      } else {
        throw new RuntimeException("Could not find Mass Charge value for " + chemical);
      }
    }

    ChemicalToMapOfMetlinIonsToIntensityTimeValues positiveWellSignalProfiles = AnalysisHelper.readScanData(
        db,
        lcmsDir,
        searchMZs,
        ScanData.KIND.POS_SAMPLE,
        plateCache,
        positiveWell,
        USE_FINE_GRAINED_TOLERANCE,
        includeIons,
        excludeIons,
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
          includeIons,
          excludeIons,
          USE_SNR_FOR_LCMS_ANALYSIS);

      negativeWellsSignalProfiles.add(peakDataNeg);
    }

    Map<MoleculeAndItsMetlinIon, XZ> snrResults =
        WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForNormalWells(
            positiveWellSignalProfiles, negativeWellsSignalProfiles, includeIons, searchMZs);

    Map<MoleculeAndItsMetlinIon, String> plottingFileMappings =
        ChemicalToMapOfMetlinIonsToIntensityTimeValues.plotPositiveAndNegativeControlsForEachMZ(
            searchMZs, allWells, positiveWellSignalProfiles, negativeWellsSignalProfiles, plottingDir, includeIons);

    Map<MoleculeAndItsMetlinIon, Pair<String, XZ>> mzToPlotDirAndSNR = new HashMap<>();
    for (Map.Entry<MoleculeAndItsMetlinIon, XZ> entry : snrResults.entrySet()) {
      String plottingPath = plottingFileMappings.get(entry.getKey());
      XZ snr = entry.getValue();

      if (plottingDir == null || snr == null) {
        System.err.format("Plotting directory or snr is null\n");
      }

      mzToPlotDirAndSNR.put(entry.getKey(), Pair.of(plottingPath, snr));
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

    List<String> predictedChemicalsByMassCharge = new ArrayList<>();
    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      for (String product : prediction.getProductInchis()) {
        predictedChemicalsByMassCharge.add(MassCalculator.calculateMass(product).toString());
      }
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      // Get experimental setup ie. positive and negative wells from config file
      List<LCMSWell> positiveWells = new ArrayList<>();
      List<LCMSWell> negativeWells = new ArrayList<>();

      TSVParser parser = new TSVParser();
      parser.parse(new File(args[1]));
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

      for (LCMSWell positiveWell : positiveWells) {
        String outAnalysis = outputPrefix + "_" + positiveWell.getId().toString() + "." + CSV_FORMAT;
        String[] headerStrings = {"Chemical", "Ion", "Positive Sample ID", "SNR", "Time", "Plots"};
        CSVPrinter printer = new CSVPrinter(new FileWriter(outAnalysis), CSVFormat.DEFAULT.withHeader(headerStrings));

        Map<MoleculeAndItsMetlinIon, Pair<String, XZ>> result =
            getSnrResultsAndPlotDiagnosticsForEachMoleculeAndItsMetlinIon(
                lcmsDir,
                db,
                positiveWell,
                negativeWells,
                plateCache,
                predictedChemicalsByMassCharge,
                plottingDirectory,
                includeIons,
                excludeIons);

        for (Map.Entry<MoleculeAndItsMetlinIon, Pair<String, XZ>> mzToPlotAndSnr : result.entrySet()) {
          String[] resultSet = {
              mzToPlotAndSnr.getKey().getInchi(),
              mzToPlotAndSnr.getKey().getIon(),
              positiveWell.getMsid(),
              mzToPlotAndSnr.getValue().getRight().getIntensity().toString(),
              mzToPlotAndSnr.getValue().getRight().getTime().toString(),
              mzToPlotAndSnr.getValue().getLeft()
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
      }
    }
  }
}