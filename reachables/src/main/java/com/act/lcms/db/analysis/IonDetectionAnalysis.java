package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class IonDetectionAnalysis <T extends PlateWell<T>> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(IonDetectionAnalysis.class);
  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  private static final boolean USE_FINE_GRAINED_TOLERANCE = false;
  private static final String DEFAULT_ION = "M+H";
  private static final Double MIN_INTENSITY_THRESHOLD = 10000.0;
  private static final Double MIN_SNR_THRESHOLD = 1000.0;
  private static final Double MIN_TIME_THRESHOLD = 15.0;
  private static final String OPTION_LCMS_FILE_DIRECTORY = "d";
  // The OPTION_INPUT_PREDICTION_CORPUS file is a json formatted file that is serialized from the class "L2PredictionCorpus"
  // or a list of inchis.
  private static final String OPTION_INPUT_PREDICTION_CORPUS = "s";
  private static final String OPTION_OUTPUT_PREFIX = "o";
  private static final String OPTION_PLOTTING_DIR = "p";
  private static final String OPTION_INCLUDE_IONS = "i";
  private static final String OPTION_LIST_OF_INCHIS_INPUT_FILE = "f";
  // This input file is structured as a tsv file with the following schema:
  //    WELL_TYPE  PLATE_BARCODE  WELL_ROW  WELL_COLUMN
  // eg.   POS        12389        0           1
  private static final String OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE = "t";
  private static final String HEADER_WELL_TYPE = "WELL_TYPE";
  private static final String HEADER_WELL_ROW = "WELL_ROW";
  private static final String HEADER_WELL_COLUMN = "WELL_COLUMN";
  private static final String HEADER_PLATE_BARCODE = "PLATE_BARCODE";
  private static final String FAKE_CHEM_PREFIX = "CHEM_";

  private static final Set<String> ALL_HEADERS =
      new HashSet<>(Arrays.asList(HEADER_WELL_TYPE, HEADER_WELL_ROW, HEADER_WELL_COLUMN, HEADER_PLATE_BARCODE));
  private static Double OVERALL_PROGRESS_OF_RUN = 0.0;

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class takes as input an experimental setup containing positive wells and negative control well. Along with this ",
      "the class also takes in a list of chemicals to be validated by the LCMS analysis. It then performs SNR analysis ",
      "to detect which chemicals are strongly represented in the LCMS analysis and outputs those in a json file per positive sample"
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
    add(Option.builder(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE)
        .argName("input positive and negative control wells")
        .desc("A tsv file containing positive and negative wells")
        .hasArg().required()
        .longOpt("wells-config")
    );
    add(Option.builder(OPTION_LIST_OF_INCHIS_INPUT_FILE)
        .argName("file input type")
        .desc("If this option is specified, the input corpus is a list of inchis")
        .longOpt("file-input-type")
    );
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  // Instance variables
  private File lcmsDir;
  private String plottingDirPath;
  private List<T> positiveWells;
  private List<T> negativeWells;
  private HashMap<Integer, Plate> plateCache;
  private List<Pair<String, Double>> listOfMassCharges;
  private DB db;
  private Double progress;

  public IonDetectionAnalysis(File lcmsDir, List<T> positiveWells, List<T> negativeWells, String plottingDirPath,
                              HashMap<Integer, Plate> plateCache, List<Pair<String, Double>> listOfMassCharges, DB db) {
    this.lcmsDir = lcmsDir;
    this.positiveWells = positiveWells;
    this.negativeWells = negativeWells;
    this.plateCache = plateCache;
    this.listOfMassCharges = listOfMassCharges;
    this.db = db;
    this.plottingDirPath = plottingDirPath;
    this.progress = 0.0;
  }

  public static Map<Double, Set<Pair<String, String>>> constructMassChargeToChemicalIonsFromInputFile(
      File inputPredictionCorpus, Set<String> includeIons, Boolean listOfInchisFormat)
      throws IOException {

    List<String> products = new ArrayList<>();
    if (listOfInchisFormat) {
      try (BufferedReader br = new BufferedReader(new FileReader(inputPredictionCorpus))) {
        // Get the inchis from input file
        String product;
        while ((product = br.readLine()) != null) {
          products.add(product.trim());
        }
      }
    } else {
      products.addAll(L2PredictionCorpus.readPredictionsFromJsonFile(inputPredictionCorpus).getUniqueProductInchis());
    }

    Map<Double, Set<Pair<String, String>>> massChargeToChemicalAndIon = new HashMap<>();

    for (String inchi : products) {
      try {
        // Assume the ion modes are all positive!
        Map<String, Double> allMasses = MS1.getIonMasses(MassCalculator.calculateMass(inchi), MS1.IonMode.POS);
        Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, includeIons, null);

        for (Map.Entry<String, Double> entry : metlinMasses.entrySet()) {
          Set<Pair<String, String>> res = massChargeToChemicalAndIon.get(entry.getValue());
          if (res == null) {
            res = new HashSet<>();
            massChargeToChemicalAndIon.put(entry.getValue(), res);
          }

          res.add(Pair.of(inchi, entry.getKey()));
        }
      } catch (Exception e) {
        LOGGER.error("Caught exception when trying to import %s", inchi);
      }
    }

    return massChargeToChemicalAndIon;
  }

  public static Pair<Set<Pair<String, Double>>, Map<String, Double>>
  constructFakeNameToMassCharge(Map<Double, Set<Pair<String, String>>> massChargeToChemicalAndIon) {

    Set<Pair<String, Double>> searchMZs = new HashSet<>();
    Integer chemicalCounter = 0;
    Map<String, Double> chemIDToMassCharge = new HashMap<>();
    for (Double massCharge : massChargeToChemicalAndIon.keySet()) {
      String chemID = String.format(FAKE_CHEM_PREFIX + "%05d", chemicalCounter);
      chemIDToMassCharge.put(chemID, massCharge);
      searchMZs.add(Pair.of(chemID, massCharge));
      chemicalCounter++;
    }

    return Pair.of(searchMZs, chemIDToMassCharge);
  }

  public static Map<ScanData.KIND, List<LCMSWell>> readInputExperimentalSetup(DB db, String inputFile)
      throws IOException, SQLException, ClassNotFoundException {

    TSVParser parser = new TSVParser();
    parser.parse(new File(inputFile));
    Set<String> headerSet = new HashSet<>(parser.getHeader());

    if (!headerSet.equals(ALL_HEADERS)) {
      String headers = StringUtils.join(ALL_HEADERS, ",");
      LOGGER.error("Invalid header types! The allowed header types are: %s", headers);
      System.exit(1);
    }

    Map<ScanData.KIND, List<LCMSWell>> result = new HashMap<>();

    HashMap<String, Plate> plateCache = new HashMap<>();

    for (Map<String, String> row : parser.getResults()) {
      String wellType = row.get(HEADER_WELL_TYPE);
      String barcode = row.get(HEADER_PLATE_BARCODE);
      Integer rowCoordinate = Integer.parseInt(row.get(HEADER_WELL_ROW));
      Integer columnCoordinate = Integer.parseInt(row.get(HEADER_WELL_COLUMN));

      Plate plate = plateCache.get(barcode);
      if (plate == null) {
        plate = Plate.getPlateByBarcode(db, barcode);
        plateCache.put(barcode, plate);
      }

      LCMSWell well = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, plate.getId(), rowCoordinate, columnCoordinate);

      if (well == null) {
        LOGGER.error("Well plate id %d, row %d and col %d does not exist", plate.getId(), rowCoordinate, columnCoordinate);
        System.exit(1);
      }

      if (wellType.equals("POS")) {
        List<LCMSWell> values = result.get(ScanData.KIND.POS_SAMPLE);
        if (values == null) {
          values = new ArrayList<>();
          result.put(ScanData.KIND.POS_SAMPLE, values);
        }
        values.add(well);
      } else {
        List<LCMSWell> values = result.get(ScanData.KIND.NEG_CONTROL);
        if (values == null) {
          values = new ArrayList<>();
          result.put(ScanData.KIND.NEG_CONTROL, values);
        }

        values.add(well);
      }
    }

    return result;
  }

  /**
   * Get the intensity-time values for a well
   * @param well The well to analyze
   * @param kindOfWell The type of well in the experimental setup (ie pos or neg control)
   * @return The intensity-time values for a well
   * @throws Exception
   */
  public ChemicalToMapOfMetlinIonsToIntensityTimeValues
  getIntensityTimeProfileForMassChargesInWell(T well, ScanData.KIND kindOfWell) throws Exception {

    Plate plate = plateCache.get(well.getPlateId());
    if (plate == null) {
      plate = Plate.getPlateById(db, well.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    ChemicalToMapOfMetlinIonsToIntensityTimeValues signalProfile = AnalysisHelper.readScanData(
        db,
        lcmsDir,
        listOfMassCharges,
        kindOfWell,
        plateCache,
        well,
        USE_FINE_GRAINED_TOLERANCE,
        USE_SNR_FOR_LCMS_ANALYSIS);

    if (signalProfile == null) {
      LOGGER.error("No signal data available.");
      System.exit(1);
    }

    return signalProfile;
  }

  /**
   * This function gets the intensity time values for all positive and negative wells
   * @return A mapping for well type to list of well and intensity-time value pairs.
   * @throws Exception
   */
  public Map<ScanData.KIND, List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>>>
  getIntensityTimeValuesForMassChargesInPositiveAndNegativeWells() throws Exception {

    Map<ScanData.KIND, List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>>>
        designUnitToWellIntensityTimeValuePairs = new HashMap<>();

    for (T positiveWell : positiveWells) {
      LOGGER.info("Reading scan data for positive well");

      ChemicalToMapOfMetlinIonsToIntensityTimeValues positiveWellSignalProfiles =
          getIntensityTimeProfileForMassChargesInWell(positiveWell, ScanData.KIND.POS_SAMPLE);

      if (positiveWellSignalProfiles == null) {
        LOGGER.error("Peak positive analysis was null");
        System.exit(1);
      }

      List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>> values =
          designUnitToWellIntensityTimeValuePairs.get(ScanData.KIND.POS_SAMPLE);

      if (values == null) {
        values = new ArrayList<>();
        designUnitToWellIntensityTimeValuePairs.put(ScanData.KIND.POS_SAMPLE, values);
      }

      values.add(Pair.of(positiveWell, positiveWellSignalProfiles));
      updateProgress();
    }

    for (T negativeWell : negativeWells) {
      LOGGER.info("Reading scan data for negative well");

      ChemicalToMapOfMetlinIonsToIntensityTimeValues negativeWellSignalProfiles =
          getIntensityTimeProfileForMassChargesInWell(negativeWell, ScanData.KIND.NEG_CONTROL);

      if (negativeWellSignalProfiles == null) {
        LOGGER.error("Peak negative analysis was null");
        System.exit(1);
      }

      List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>> values =
          designUnitToWellIntensityTimeValuePairs.get(ScanData.KIND.NEG_CONTROL);

      if (values == null) {
        values = new ArrayList<>();
        designUnitToWellIntensityTimeValuePairs.put(ScanData.KIND.NEG_CONTROL, values);
      }

      values.add(Pair.of(negativeWell, negativeWellSignalProfiles));
      updateProgress();
    }

    return designUnitToWellIntensityTimeValuePairs;
  }

  /**
   * This function does the SNR analysis for each positive well and constructs plots for each mass charge for each
   * positive well run
   * @return A map of positive well to map of chem id to plotting file path to pair of max intensity, time and SNR.
   * @throws Exception
   */
  public Map<T, Map<String, Pair<String, Pair<XZ, Double>>>>
  getSnrAndPlotResultsForMassChargesForEachPositiveWell() throws Exception {

    // Get signal profiles for each positive and negative wells once. This is the rate limiting step of the computation.
    Map<ScanData.KIND, List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>>> designUnitToListOfWellIntensityTimeValues =
        getIntensityTimeValuesForMassChargesInPositiveAndNegativeWells();

    // This is the object the final results are stored in.
    Map<T, Map<String, Pair<String, Pair<XZ, Double>>>> positiveWellToMapOfChemicalToSNRResults = new HashMap<>();

    List<Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues>> positiveWellResults =
        designUnitToListOfWellIntensityTimeValues.get(ScanData.KIND.POS_SAMPLE);

    List<ChemicalToMapOfMetlinIonsToIntensityTimeValues> negativeWellsSignalProfiles =
        designUnitToListOfWellIntensityTimeValues.get(ScanData.KIND.NEG_CONTROL).stream().map(pair -> pair.getRight())
            .collect(Collectors.toList());

    for (Pair<T, ChemicalToMapOfMetlinIonsToIntensityTimeValues> value : positiveWellResults) {
      T positiveWell = value.getLeft();
      ChemicalToMapOfMetlinIonsToIntensityTimeValues positiveWellSignalProfile = value.getRight();

      Map<String, Pair<XZ, Double>> snrResults =
          WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForWells(
              positiveWellSignalProfile, negativeWellsSignalProfiles, listOfMassCharges);

      List<T> positiveWellAndNegativeWells = new ArrayList<>();
      positiveWellAndNegativeWells.add(positiveWell);
      positiveWellAndNegativeWells.addAll(negativeWells);

      // This variable is used as a part of the file path dir to uniquely identify the pos/neg wells for the chemical.
      StringBuilder indexedPath = new StringBuilder();
      for (T well : positiveWellAndNegativeWells) {
        indexedPath.append(Integer.toString(well.getId()) + "-");
      }

      Map<String, String> plottingFileMappings =
          ChemicalToMapOfMetlinIonsToIntensityTimeValues.plotPositiveAndNegativeControlsForEachMZ(
              listOfMassCharges, indexedPath.toString(), positiveWellSignalProfile, negativeWellsSignalProfiles, plottingDirPath);

      Map<String, Pair<String, Pair<XZ, Double>>> mzToPlotDirAndSNR = new HashMap<>();
      for (Map.Entry<String, Pair<XZ, Double>> entry : snrResults.entrySet()) {
        String plottingPath = plottingFileMappings.get(entry.getKey());
        XZ snr = entry.getValue().getLeft();

        if (plottingDirPath == null || snr == null) {
          System.err.format("Plotting directory or snr is null");
          System.exit(1);
        }

        mzToPlotDirAndSNR.put(entry.getKey(), Pair.of(plottingPath, entry.getValue()));
      }

      positiveWellToMapOfChemicalToSNRResults.put(positiveWell, mzToPlotDirAndSNR);
    }

    return positiveWellToMapOfChemicalToSNRResults;
  }

  /**
   * This function runs the lcms ion mining analysis based on mass charges that bin the input chemical+ion combinations
   * and writes the plots the resulting pos+neg control intensity-time values, along with the SNR results,
   * to the final json file.
   * @param chemIDToMassCharge Fake chem name (representing each bin) to mass charge.
   * @param massChargeToChemicalAndIon Mass charge value to pair of Chemical and Ion combination
   * @param outputPrefix The output prefix to write output results to.
   * @throws Exception
   */
  public void runLCMSMiningAnalysisAndPlotResults(Map<String, Double> chemIDToMassCharge,
                                                  Map<Double, Set<Pair<String, String>>> massChargeToChemicalAndIon,
                                                  String outputPrefix)
      throws Exception {

    Map<T, Map<String, Pair<String, Pair<XZ, Double>>>> lcmsAnalysisResultForEachPositiveWell =
        getSnrAndPlotResultsForMassChargesForEachPositiveWell();

    List<List<IonAnalysisInterchangeModel.ResultForMZ>> allExperimentalResults = new ArrayList<>();

    int numberOfMassChargeHits = 0;

    for (T positiveWell : lcmsAnalysisResultForEachPositiveWell.keySet()) {
      List<IonAnalysisInterchangeModel.ResultForMZ> experimentalResults = new ArrayList<>();
      Map<String, Pair<String, Pair<XZ, Double>>> result = lcmsAnalysisResultForEachPositiveWell.get(positiveWell);

      for (Map.Entry<String, Pair<String, Pair<XZ, Double>>> mzToPlotAndSnr : result.entrySet()) {
        Double massCharge = chemIDToMassCharge.get(mzToPlotAndSnr.getKey());
        String plot = mzToPlotAndSnr.getValue().getLeft();
        Double intensity = mzToPlotAndSnr.getValue().getRight().getLeft().getIntensity();
        Double time = mzToPlotAndSnr.getValue().getRight().getLeft().getTime();
        Double snr = mzToPlotAndSnr.getValue().getRight().getRight();

        IonAnalysisInterchangeModel.ResultForMZ resultForMZ = new IonAnalysisInterchangeModel.ResultForMZ(massCharge);

        if (intensity > MIN_INTENSITY_THRESHOLD &&
            time > MIN_TIME_THRESHOLD &&
            snr > MIN_SNR_THRESHOLD) {
          resultForMZ.setIsValid(true);
          numberOfMassChargeHits++;
        } else {
          resultForMZ.setIsValid(false);
        }

        Set<Pair<String, String>> inchisAndIon = massChargeToChemicalAndIon.get(massCharge);
        for (Pair<String, String> pair : inchisAndIon) {
          String inchi = pair.getLeft();
          String ion = pair.getRight();
          IonAnalysisInterchangeModel.HitOrMiss hitOrMiss = new IonAnalysisInterchangeModel.HitOrMiss(inchi, ion, snr, time, intensity, plot);
          resultForMZ.addMolecule(hitOrMiss);
        }

        experimentalResults.add(resultForMZ);
      }

      IonAnalysisInterchangeModel ionAnalysisInterchangeModel = new IonAnalysisInterchangeModel(experimentalResults);

      String outAnalysis = outputPrefix + "_" + positiveWell.getId().toString() + ".json";
      ionAnalysisInterchangeModel.writeToJsonFile(new File(outAnalysis));
      allExperimentalResults.add(experimentalResults);
    }

    LOGGER.info("The number of mass charge hits are %d out of %d", numberOfMassChargeHits, massChargeToChemicalAndIon.keySet().size());

    if (positiveWells.size() > 1) {

      LOGGER.info("Conducting post processing since we have more than one positive well");

      // Post process analysis
      String outAnalysis = outputPrefix + "_post_process.json";
      List<IonAnalysisInterchangeModel.ResultForMZ> experimentalResults = new ArrayList<>();
      for (int i = 0; i < allExperimentalResults.get(0).size(); i++) {
        IonAnalysisInterchangeModel.ResultForMZ rep = allExperimentalResults.get(0).get(i);
        IonAnalysisInterchangeModel.ResultForMZ resultForMZ = new IonAnalysisInterchangeModel.ResultForMZ(rep.getMz());
        Boolean areAllValid = true;

        for (List<IonAnalysisInterchangeModel.ResultForMZ> res : allExperimentalResults) {
          if (!res.get(i).getIsValid()) {
            areAllValid = false;
          }
          resultForMZ.addMolecules(res.get(i).getMolecules());
        }

        resultForMZ.setIsValid(areAllValid);
        experimentalResults.add(resultForMZ);
      }

      IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel(experimentalResults);
      model.writeToJsonFile(new File(outAnalysis));
    }
  }

  /**
   * This function updates the progress of the run. Since the runtime is significantly driven by reading scan data
   * for each well, the function is called after each scan read is finished for each well. Therefore, the total
   * progress is divided by the total number of wells.
   */
  public void updateProgress() {
    progress += 100.0/(positiveWells.size() + negativeWells.size());
    LOGGER.info("Progress: %f", progress);
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
      System.err.format("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File lcmsDir = new File(cl.getOptionValue(OPTION_LCMS_FILE_DIRECTORY));
    if (!lcmsDir.isDirectory()) {
      System.err.format("File at %s is not a directory", lcmsDir.getAbsolutePath());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    String plottingDirectory = cl.getOptionValue(OPTION_PLOTTING_DIR);

    // Get include and excluse ions from command line
    Set<String> includeIons;
    if (cl.hasOption(OPTION_INCLUDE_IONS)) {
      String[] ionNames = cl.getOptionValues(OPTION_INCLUDE_IONS);
      includeIons = new HashSet<>(Arrays.asList(ionNames));
      LOGGER.info("Including ions in search: %s", StringUtils.join(includeIons, ", "));
    } else {
      includeIons = new HashSet<>();
      includeIons.add(DEFAULT_ION);
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      //ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      File inputPredictionCorpus = new File(cl.getOptionValue(OPTION_INPUT_PREDICTION_CORPUS));

      Map<Double, Set<Pair<String, String>>> massChargeToChemicalAndIon =
          constructMassChargeToChemicalIonsFromInputFile(inputPredictionCorpus, includeIons, cl.hasOption(OPTION_LIST_OF_INCHIS_INPUT_FILE));

      Pair<Set<Pair<String, Double>>, Map<String, Double>> values = constructFakeNameToMassCharge(massChargeToChemicalAndIon);
      Set<Pair<String, Double>> searchMZs = values.getLeft();
      Map<String, Double> chemIDToMassCharge = values.getRight();

      LOGGER.info("The number of mass charges are: %d", searchMZs.size());

      Map<ScanData.KIND, List<LCMSWell>> wellTypeToLCMSWells = readInputExperimentalSetup(db,
          cl.getOptionValue(OPTION_INPUT_POSITIVE_AND_NEGATIVE_CONTROL_WELLS_FILE));

      // Get experimental setup ie. positive and negative wells from config file
      List<LCMSWell> positiveWells = wellTypeToLCMSWells.get(ScanData.KIND.POS_SAMPLE);
      List<LCMSWell> negativeWells = wellTypeToLCMSWells.get(ScanData.KIND.NEG_CONTROL);

      LOGGER.info("Number of positive wells is: %d", positiveWells.size());
      LOGGER.info("Number of negative wells is: %d", negativeWells.size());

      HashMap<Integer, Plate> plateCache = new HashMap<>();
      String outputPrefix = cl.getOptionValue(OPTION_OUTPUT_PREFIX);

      List<Pair<String, Double>> listOfMassCharges = new ArrayList<>(searchMZs);

      IonDetectionAnalysis<LCMSWell> ionDetectionAnalysis = new IonDetectionAnalysis<LCMSWell>(lcmsDir, positiveWells,
          negativeWells, plottingDirectory, plateCache, listOfMassCharges, db);

      ionDetectionAnalysis.runLCMSMiningAnalysisAndPlotResults(chemIDToMassCharge, massChargeToChemicalAndIon, outputPrefix);
    }
  }
}
