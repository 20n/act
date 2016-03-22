package com.act.lcms.db.io;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.db.analysis.AnalysisHelper;
import com.act.lcms.db.analysis.ChemicalToMapOfMetlinIonsToIntensityTimeValues;
import com.act.lcms.db.analysis.ScanData;
import com.act.lcms.db.analysis.StandardIonAnalysis;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.analysis.WaveformAnalysis;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.CuratedChemical;
import com.act.lcms.db.model.CuratedStandardMetlinIon;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardIonResult;
import com.act.lcms.db.model.StandardWell;
import com.act.utils.TSVWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExportStandardIonResultsFromDB {
  public static final String OPTION_DIRECTORY = "d";
  public static final String TSV_FORMAT = "tsv";
  public static final String OPTION_CONSTRUCT = "C";
  public static final String OPTION_CHEMICALS = "c";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String NULL_VALUE = "NULL";
  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class is used to export relevant standard ion analysis data to the scientist from the " +
      "standard_ion_results DB for manual assessment (done in github) through a TSV file. The inputs to this " +
      "class either be an individual standard chemical name OR a construct pathway."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  private static final String DEFAULT_ION = "M+H";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>(0));

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
        .desc("The construct to get results from")
        .hasArg()
    );
    add(Option.builder(OPTION_CHEMICALS)
        .argName("a comma separated list of chemical names")
        .desc("A list of chemicals to get standard ion data from")
        .hasArgs().valueSeparator(',')
    );
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("The prefix name")
        .desc("The prefix of the output file")
        .hasArg()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public enum STANDARD_ION_HEADER_FIELDS {
    CHEMICAL,
    BEST_ION_FROM_ALGO,
    MANUAL_PICK,
    AUTHOR,
    NOTE,
    DIAGNOSTIC_PLOTS,
    PLATE_METADATA,
    SNR_TIME,
    STANDARD_ION_RESULT_ID
  };

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
      HELP_FORMATTER.printHelp(ExportStandardIonResultsFromDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ExportStandardIonResultsFromDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      List<String> chemicalNames = new ArrayList<>();
      if (cl.hasOption(OPTION_CONSTRUCT)) {
        // Extract the chemicals in the pathway and their product masses, then look up info on those chemicals
        List<Pair<ChemicalAssociatedWithPathway, Double>> productMasses =
            Utils.extractMassesForChemicalsAssociatedWithConstruct(db, cl.getOptionValue(OPTION_CONSTRUCT));

        for (Pair<ChemicalAssociatedWithPathway, Double> pair : productMasses) {
          chemicalNames.add(pair.getLeft().getChemical());
        }
      }

      if (cl.hasOption(OPTION_CHEMICALS)) {
        chemicalNames.addAll(Arrays.asList(cl.getOptionValues(OPTION_CHEMICALS)));
      }

      if (chemicalNames.size() == 0) {
        System.err.format("No chemicals can be found from the input query.\n");
        System.exit(-1);
      }

      List<String> standardIonHeaderFields = new ArrayList<>();
      for (STANDARD_ION_HEADER_FIELDS field : STANDARD_ION_HEADER_FIELDS.values()) {
        standardIonHeaderFields.add(field.name());
      }

      String outAnalysis;
      if (cl.hasOption(OPTION_OUTPUT_PREFIX)) {
        outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + TSV_FORMAT;
      } else {
        outAnalysis = String.join("-", chemicalNames) + "." + TSV_FORMAT;
      }

      File lcmsDir = new File(cl.getOptionValue(OPTION_DIRECTORY));
      if (!lcmsDir.isDirectory()) {
        System.err.format("File at %s is not a directory\n", lcmsDir.getAbsolutePath());
        HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }

      List<StandardIonResult> ionResults = new ArrayList<>();
      MS1 mm = new MS1(false, false);
      List<String> graphLabels = new ArrayList<>();
      List<Double> yMaxList = new ArrayList<>();
      String outData = "test.data";
      String outImg = "test.pdf";

      try (FileOutputStream fos = new FileOutputStream(outData)) {
        for (String chemicalName : chemicalNames) {
          List<StandardIonResult> getResultByChemicalName = StandardIonResult.getByChemicalName(db, chemicalName);
          if (getResultByChemicalName != null) {
            ionResults.addAll(getResultByChemicalName);

            //Arrange results based on media
            Map<String, List<StandardIonResult>> categories = new HashMap<>();

            for (StandardIonResult result : getResultByChemicalName) {
              if (StandardWell.isMediaMeOH(
                  StandardWell.getInstance().getById(db, result.getStandardWellId()).getMedia())) {
                List<StandardIonResult> res = categories.get(StandardWell.MEDIA_TYPE.MEOH.name());
                if (res == null) {
                  res = new ArrayList<>();
                  res.add(result);
                }
                categories.put(StandardWell.MEDIA_TYPE.MEOH.name(), res);
              } else if (StandardWell.doesMediaContainYeastExtract(
                  StandardWell.getInstance().getById(db, result.getStandardWellId()).getMedia())) {
                List<StandardIonResult> res = categories.get(StandardWell.MEDIA_TYPE.WATER.name());
                if (res == null) {
                  res = new ArrayList<>();
                  res.add(result);
                }
                categories.put(StandardWell.MEDIA_TYPE.WATER.name(), res);
              } else if (StandardWell.doesMediaContainWater(
                  StandardWell.getInstance().getById(db, result.getStandardWellId()).getMedia())) {
                List<StandardIonResult> res = categories.get(StandardWell.MEDIA_TYPE.YEAST.name());
                if (res == null) {
                  res = new ArrayList<>();
                  res.add(result);
                }
                categories.put(StandardWell.MEDIA_TYPE.YEAST.name(), res);
              }
            }

            for (String media : categories.keySet()) {

              for (StandardIonResult standardIonResult : categories.get(media)) {

                StandardWell well = StandardWell.getInstance().getById(db, standardIonResult.getStandardWellId());
                Plate plate = Plate.getPlateById(db, well.getPlateId());

                List<ScanFile> positiveScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
                    db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
                ScanFile representativePositiveScanFile = positiveScanFiles.get(0);

                Double mzValue = Utils.extractMassForChemical(db, standardIonResult.getChemical());

                File localScanFile = new File(lcmsDir, representativePositiveScanFile.getFilename());
                if (!localScanFile.exists() && localScanFile.isFile()) {
                  System.err.format("WARNING: could not find regular file at expected path: %s\n",
                      localScanFile.getAbsolutePath());
                  continue;
                }

                MS1.IonMode mode = MS1.IonMode.valueOf(representativePositiveScanFile.getMode().toString().toUpperCase());
                Map<String, Double> allMasses = mm.getIonMasses(mzValue, mode);
                Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, EMPTY_SET, EMPTY_SET);

                MS1ScanForWellAndMassCharge ms1ScanResultsCache = new MS1ScanForWellAndMassCharge();
                MS1ScanForWellAndMassCharge ms1ScanResultsForPositiveControl =
                    ms1ScanResultsCache.getByPlateIdPlateRowPlateColUseSnrScanFileChemical(db, plate, well, true, representativePositiveScanFile,
                        standardIonResult.getChemical(), metlinMasses, localScanFile);

                ScanData encapsulatedDataForPositiveControl =
                    new ScanData<StandardWell>(ScanData.KIND.STANDARD, plate, well, representativePositiveScanFile, standardIonResult.getChemical(),
                        metlinMasses, ms1ScanResultsForPositiveControl);

                Double maxIntensity = 0.0d;
                List<String> setOfIons = new ArrayList<>();
                int ylabelCounter = 0;

                if (standardIonResult.getBestMetlinIon().equals(DEFAULT_ION)) {
                  maxIntensity = encapsulatedDataForPositiveControl.getMs1ScanResults().getMaxIntensityForIon(DEFAULT_ION);
                  setOfIons.add(DEFAULT_ION);
                  ylabelCounter += 1;
                } else {
                  maxIntensity =
                      Math.max(encapsulatedDataForPositiveControl.getMs1ScanResults().getMaxIntensityForIon(DEFAULT_ION),
                          encapsulatedDataForPositiveControl.getMs1ScanResults().getMaxIntensityForIon(standardIonResult.getBestMetlinIon()));
                  setOfIons.add(standardIonResult.getBestMetlinIon());
                  setOfIons.add(DEFAULT_ION);
                  ylabelCounter += 2;
                }

                ScanData encapsulatedDataForNegativeControl = null;

                if (StandardWell.doesMediaContainYeastExtract(well.getMedia())) {
                  StandardWell negativeControlWell = StandardWell.getInstance().getById(db, standardIonResult.getNegativeWellIds().get(0));
                  Plate negativeControlPlate = Plate.getPlateById(db, negativeControlWell.getPlateId());

                  List<ScanFile> negativeControlScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
                      db, negativeControlWell.getPlateId(), negativeControlWell.getPlateRow(), negativeControlWell.getPlateColumn());

                  ScanFile representativeNegativeScanFile = negativeControlScanFiles.get(0);

                  MS1ScanForWellAndMassCharge ms1ScanResultsForNegativeControl =
                      ms1ScanResultsCache.getByPlateIdPlateRowPlateColUseSnrScanFileChemical(db, negativeControlPlate, well, true, representativeNegativeScanFile,
                          negativeControlWell.getChemical(), metlinMasses, new File(lcmsDir, representativeNegativeScanFile.getFilename()));

                  encapsulatedDataForNegativeControl =
                      new ScanData<StandardWell>(ScanData.KIND.STANDARD, plate, negativeControlWell, representativeNegativeScanFile, negativeControlWell.getChemical(),
                          metlinMasses, ms1ScanResultsForNegativeControl);

                  maxIntensity = Math.max(maxIntensity, encapsulatedDataForNegativeControl.getMs1ScanResults().getMaxYAxis());
                  ylabelCounter += 1;
                }

                for (int i = 0; i < ylabelCounter; i++) {
                  yMaxList.add(maxIntensity);
                }

                for (String ion : setOfIons) {
                  Set<String> singletonSet = new HashSet<>();
                  singletonSet.add(ion);

                  List<String> labels =
                      AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, encapsulatedDataForPositiveControl,
                          false, false, singletonSet);

                  if (encapsulatedDataForNegativeControl != null && !ion.equals(DEFAULT_ION)) {
                    List<String> negativeLabels =
                        AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, encapsulatedDataForNegativeControl,
                            false, false, singletonSet);

                    labels.addAll(negativeLabels);
                  }

                  List<String> newLabels = new ArrayList<>();
                  String plateMetadata = well.getMedia() + " " + well.getConcentration();
                  XZ intensityAndTimeOfBestIon = standardIonResult.getAnalysisResults().get(standardIonResult.getBestMetlinIon());

                  Double intensity = Math.max(intensityAndTimeOfBestIon.getIntensity(), 10000.0);

                  String snrAndTime = String.format("%.2f SNR at %.2fs", intensity,
                      intensityAndTimeOfBestIon.getTime());

                  String additionalInfo = String.format("\n %s %s", plateMetadata, snrAndTime);

                  for (int i = 0; i < labels.size(); i++) {
                    newLabels.add(i, labels.get(i) + additionalInfo);
                  }

                  graphLabels.addAll(newLabels);
                }
              }
            }
          }
        }
      }

      // We need to pass the yMax values as an array to the Gnuplotter.
      Double fontScale = null;
      Double[] yMaxes = yMaxList.toArray(new Double[yMaxList.size()]);
      Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
      plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time",
          null, "intensity", "pdf", null, null, yMaxes, outImg + ".gnuplot");

      TSVWriter<String, String> resultsWriter = new TSVWriter<>(standardIonHeaderFields);
      resultsWriter.open(new File(outAnalysis));

      //TODO: Handle the case where no standard chemicals are found.
      for (StandardIonResult ionResult : ionResults) {
        StandardWell well = StandardWell.getInstance().getById(db, ionResult.getStandardWellId());
        Plate plateForWellToAnalyze = Plate.getPlateById(db, well.getPlateId());
        String plateMetadata = plateForWellToAnalyze.getBarcode() + " " + well.getCoordinatesString() + " " +
            well.getMedia() + " " + well.getConcentration();

        String bestIon = ionResult.getBestMetlinIon();
        XZ intensityAndTimeOfBestIon = ionResult.getAnalysisResults().get(bestIon);
        String snrAndTime = String.format("%.2f SNR at %.2fs", intensityAndTimeOfBestIon.getIntensity(),
            intensityAndTimeOfBestIon.getTime());

        Map<String, String> diagnosticPlots = new HashMap<>();
        diagnosticPlots.put(bestIon, ionResult.getPlottingResultFilePaths().get(bestIon));
        diagnosticPlots.put(DEFAULT_ION, ionResult.getPlottingResultFilePaths().get(DEFAULT_ION));
        String diagnosticPlotsString = OBJECT_MAPPER.writeValueAsString(diagnosticPlots);

        String manualMetlinIonPick;
        String note;
        String author;
        if (ionResult.getManualOverrideId() == null) {
          manualMetlinIonPick = NULL_VALUE;
          note = NULL_VALUE;
          author = NULL_VALUE;
        } else {
          CuratedStandardMetlinIon manuallyCuratedChemical =
              CuratedStandardMetlinIon.getBestMetlinIon(db, ionResult.getManualOverrideId());
          manualMetlinIonPick = manuallyCuratedChemical.getBestMetlinIon();
          note = manuallyCuratedChemical.getNote() == null ? NULL_VALUE : manuallyCuratedChemical.getNote();
          author = manuallyCuratedChemical.getAuthor();
        }

        Map<String, String> row = new HashMap<>();
        row.put(STANDARD_ION_HEADER_FIELDS.CHEMICAL.name(), ionResult.getChemical());
        row.put(STANDARD_ION_HEADER_FIELDS.PLATE_METADATA.name(), plateMetadata);
        row.put(STANDARD_ION_HEADER_FIELDS.BEST_ION_FROM_ALGO.name(), bestIon);
        row.put(STANDARD_ION_HEADER_FIELDS.SNR_TIME.name(), snrAndTime);
        row.put(STANDARD_ION_HEADER_FIELDS.MANUAL_PICK.name(), manualMetlinIonPick);
        row.put(STANDARD_ION_HEADER_FIELDS.NOTE.name(), note);
        row.put(STANDARD_ION_HEADER_FIELDS.DIAGNOSTIC_PLOTS.name(), diagnosticPlotsString);
        row.put(STANDARD_ION_HEADER_FIELDS.STANDARD_ION_RESULT_ID.name(), Integer.toString(ionResult.getId()));
        row.put(STANDARD_ION_HEADER_FIELDS.AUTHOR.name(), author);

        resultsWriter.append(row);
        resultsWriter.flush();
      }

      resultsWriter.flush();
      resultsWriter.close();
    }
  }
}
