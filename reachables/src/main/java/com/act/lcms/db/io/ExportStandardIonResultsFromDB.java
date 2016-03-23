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
import com.act.lcms.db.model.PlateWell;
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
import java.util.ListIterator;
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
  public static final String OPTION_PLOTTING_DIR = "D";

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
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.CHEMICAL.name());
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.BEST_ION_FROM_ALGO.name());
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.MANUAL_PICK.name());
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.AUTHOR.name());
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.DIAGNOSTIC_PLOTS.name());
      standardIonHeaderFields.add(STANDARD_ION_HEADER_FIELDS.NOTE.name());

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

      String plottingDirectory = cl.getOptionValue(OPTION_PLOTTING_DIR);

      TSVWriter<String, String> resultsWriter = new TSVWriter<>(standardIonHeaderFields);
      resultsWriter.open(new File(outAnalysis));

      for (String chemicalName : chemicalNames) {
        List<String> graphLabels = new ArrayList<>();
        List<Double> yMaxList = new ArrayList<>();

        String outData = plottingDirectory + "/" + chemicalName + ".data";
        String outImg = plottingDirectory + "/" + chemicalName + ".pdf";

        try (FileOutputStream fos = new FileOutputStream(outData)) {

          List<StandardIonResult> getResultByChemicalName = StandardIonResult.getByChemicalName(db, chemicalName);

          if (getResultByChemicalName != null) {

            //Get the best metlin ion across all standard ion results
            String bestMetlinIon = AnalysisHelper.scoreAndReturnBestMetlinIonFromStandardIonResults(getResultByChemicalName,
                new HashMap<>(), true, true);

            // Plot all the graphs related to the analysis

            //Arrange results based on media
            Map<String, List<StandardIonResult>> categories =
                StandardIonResult.categorizeListOfStandardWellsByMedia(db, getResultByChemicalName);

            for (Map.Entry<String, List<StandardIonResult>> mediaToListOfIonResults : categories.entrySet()) {
              for (StandardIonResult standardIonResult : mediaToListOfIonResults.getValue()) {

                StandardWell positiveWell = StandardWell.getInstance().getById(db, standardIonResult.getStandardWellId());
                String positiveControlChemical = positiveWell.getChemical();

                ChemicalToMapOfMetlinIonsToIntensityTimeValues encapsulatedDataForPositiveControl =
                    AnalysisHelper.getScanDataFromStandardIonResult(db, lcmsDir, positiveWell, positiveControlChemical);

                List<String> setOfIons = new ArrayList<>();

                if (chemicalName.equals("histidine")) {
                  System.out.println("aefw");
                }

                // Handle case for positive control
                Double maxIntensity =
                    Math.max(WaveformAnalysis.maxIntensityOfSpectra(encapsulatedDataForPositiveControl.getMetlinIonsOfChemical(positiveControlChemical).get(bestMetlinIon)),
                    WaveformAnalysis.maxIntensityOfSpectra(encapsulatedDataForPositiveControl.getMetlinIonsOfChemical(positiveControlChemical).get(DEFAULT_ION)));

//                Double maxIntensity =
//                    Math.max(encapsulatedDataForPositiveControl.getMs1ScanResults().getMaxIntensityForIon(DEFAULT_ION),
//                        encapsulatedDataForPositiveControl.getMs1ScanResults().
//                            getMaxIntensityForIon(standardIonResult.getBestMetlinIon()));

                setOfIons.add(standardIonResult.getBestMetlinIon());
                yMaxList.add(WaveformAnalysis.maxIntensityOfSpectra(encapsulatedDataForPositiveControl.getMetlinIonsOfChemical(positiveControlChemical).get(bestMetlinIon)));

                // Handle case for negative control
                ChemicalToMapOfMetlinIonsToIntensityTimeValues encapsulatedDataForNegativeControl = null;
                if (StandardWell.doesMediaContainYeastExtract(positiveWell.getMedia())) {
                  //TODO: Change the representative negative well to one that displays the highest noise in the future.
                  int representativeIndex = 0;
                  StandardWell representativeNegativeControlWell =
                      StandardWell.getInstance().getById(db, standardIonResult.getNegativeWellIds().get(representativeIndex));

                  encapsulatedDataForNegativeControl = AnalysisHelper.getScanDataFromStandardIonResult(db,
                      lcmsDir, representativeNegativeControlWell, positiveWell.getChemical());

                  Double maxYIntensityForNegativeControl =
                      WaveformAnalysis.maxIntensityOfSpectra(encapsulatedDataForNegativeControl.
                          getMetlinIonsOfChemical(representativeNegativeControlWell.getChemical()).get(bestMetlinIon));

                  yMaxList.add(maxYIntensityForNegativeControl);
                  maxIntensity = Math.max(maxIntensity, maxYIntensityForNegativeControl);
                }

                // Handle case for default ion
                if (!standardIonResult.getBestMetlinIon().equals(DEFAULT_ION)) {
                  Double maxYIntensityForDefault =
                      WaveformAnalysis.maxIntensityOfSpectra(encapsulatedDataForPositiveControl.getMetlinIonsOfChemical(positiveControlChemical).get(DEFAULT_ION));
                  setOfIons.add(DEFAULT_ION);
                  yMaxList.add(maxYIntensityForDefault);
                }

                boolean addedNegativeControl = false;
                for (String ion : setOfIons) {
                  Set<String> singletonSet = new HashSet<>();
                  singletonSet.add(ion);


                  List<String> labels =
                      AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, encapsulatedDataForPositiveControl,
                          false, false, singletonSet);

                  // Add the negative control data right after the positive control.
                  if (encapsulatedDataForNegativeControl != null && !addedNegativeControl) {
                    List<String> negativeLabels =
                        AnalysisHelper.writeScanData(fos, lcmsDir, maxIntensity, encapsulatedDataForNegativeControl,
                            false, false, singletonSet);

                    labels.addAll(negativeLabels);
                    addedNegativeControl = true;
                  }

                  String plateMetadata = positiveWell.getMedia() + " " + positiveWell.getConcentration();
                  XZ intensityAndTimeOfBestIon = standardIonResult.getAnalysisResults().get(standardIonResult.getBestMetlinIon());

                  Double intensity = Math.max(intensityAndTimeOfBestIon.getIntensity(), 10000.0);

                  String snrAndTime = String.format("%.2f SNR at %.2fs", intensity,
                      intensityAndTimeOfBestIon.getTime());

                  String additionalInfo = String.format("\n %s %s", plateMetadata, snrAndTime);

                  for (ListIterator index = labels.listIterator(); index.hasNext(); ) {
                    index.set(index.next() + additionalInfo);
                  }

                  graphLabels.addAll(labels);
                }
              }
            }

            // We need to pass the yMax values as an array to the Gnuplotter.
            Double fontScale = null;
            Double[] yMaxes = yMaxList.toArray(new Double[yMaxList.size()]);
            Gnuplotter plotter = fontScale == null ? new Gnuplotter() : new Gnuplotter(fontScale);
            plotter.plot2D(outData, outImg, graphLabels.toArray(new String[graphLabels.size()]), "time",
                null, "intensity", "pdf", null, null, yMaxes, outImg + ".gnuplot");

            Map<String, String> row = new HashMap<>();
            row.put(STANDARD_ION_HEADER_FIELDS.CHEMICAL.name(), chemicalName);
            row.put(STANDARD_ION_HEADER_FIELDS.BEST_ION_FROM_ALGO.name(), bestMetlinIon);
            row.put(STANDARD_ION_HEADER_FIELDS.DIAGNOSTIC_PLOTS.name(), outImg);

            resultsWriter.append(row);
            resultsWriter.flush();
          }
        }
      }

      resultsWriter.flush();
      resultsWriter.close();
    }
  }
}
