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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public <T extends PlateWell<T>> Pair<Map<String, String>, XZ> getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(
      File lcmsDir, DB db, T positiveWell, List<T> negativeWells, HashMap<Integer, Plate> plateCache, List<String> chemicals,
      String plottingDir) throws Exception {
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

    List<T> posWells = new ArrayList<>();
    posWells.add(positiveWell);

    List<T> allWells = new ArrayList<>();
    allWells.addAll(posWells);

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataPos = AnalysisHelper.readScanData(
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
      ChemicalToMapOfMetlinIonsToIntensityTimeValues peakDataNeg = AnalysisHelper.readScanData(
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