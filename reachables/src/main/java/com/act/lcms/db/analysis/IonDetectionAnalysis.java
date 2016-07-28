package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class IonDetectionAnalysis {
  private static final boolean USE_SNR_FOR_LCMS_ANALYSIS = true;
  public static final String CSV_FORMAT = "csv";
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_STANDARD_CHEMICAL = "sc";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_PLOTTING_DIR = "p";

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
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public static <T extends PlateWell<T>> Map<Pair<Pair<String, Double>, String>, Pair<String, XZ>> getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(
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

    List<T> allWells = new ArrayList<>();
    allWells.add(positiveWell);
    allWells.addAll(negativeWells);

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataPos = AnalysisHelper.readScanData(
        db,
        lcmsDir,
        searchMZs,
        ScanData.KIND.POS_SAMPLE,
        plateCache,
        positiveWell,
        false,
        includeIons,
        excludeIons,
        USE_SNR_FOR_LCMS_ANALYSIS);

    if (peakDataPos == null) {
      System.out.println("no positive data available");
    }

    List<ChemicalToMapOfMetlinIonsToIntensityTimeValues> peakDataNegs = new ArrayList<>();

    for (T well : negativeWells) {
      ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataNeg = AnalysisHelper.readScanData(
          db,
          lcmsDir,
          searchMZs,
          ScanData.KIND.NEG_CONTROL,
          plateCache,
          well,
          false,
          includeIons,
          excludeIons,
          USE_SNR_FOR_LCMS_ANALYSIS);

      peakDataNegs.add(peakDataNeg);
    }

    Map<Pair<Pair<String, Double>, String>, XZ> snrResults =
        WaveformAnalysis.performSNRAnalysisAndReturnMetlinIonsRankOrderedBySNRForNormalWells(
            peakDataPos,
            peakDataNegs,
            includeIons,
            searchMZs);

    Map<Pair<Pair<String, Double>, String>, String> plottingFileMappings =
        ChemicalToMapOfMetlinIonsToIntensityTimeValues.plotPositiveAndNegativeControlsForEachMZ(searchMZs, allWells, peakDataPos, peakDataNegs, plottingDir, includeIons);

    Map<Pair<Pair<String, Double>, String>, Pair<String, XZ>> mzToPlotDirAndSNR = new HashMap<>();

    for (Map.Entry<Pair<Pair<String, Double>, String>, XZ> entry : snrResults.entrySet()) {
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

    IonDetectionAnalysis ga = new IonDetectionAnalysis();

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
        String[] headerStrings = {"Chemical", "Ion", "Positive Sample ID", "SNR", "Time", "Plots"};
        CSVPrinter printer = new CSVPrinter(new FileWriter(outAnalysis), CSVFormat.DEFAULT.withHeader(headerStrings));

        String key = entry.getKey().replace(".", "");

        Plate queryPlate1 = Plate.getPlateByBarcode(db, key);
        LCMSWell positiveWell = LCMSWell.getInstance().getByPlateIdAndCoordinates(db, queryPlate1.getId(), entry.getValue().getLeft(), entry.getValue().getRight());

        Map<Pair<Pair<String, Double>, String>, Pair<String, XZ>> result =
            getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(lcmsDir, db, positiveWell, negativeWells, plateCache, inputChemicals, plottingDirectory, includeIons, excludeIons);

        for (Map.Entry<Pair<Pair<String, Double>, String>, Pair<String, XZ>> mzToPlotAndSnr : result.entrySet()) {
          String[] resultSet = {
              mzToPlotAndSnr.getKey().getLeft().getLeft(),
              mzToPlotAndSnr.getKey().getRight(),
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
        counter++;
      }
    }
  }
}