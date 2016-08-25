package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.comparators.BooleanComparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class BestMoleculesPickerFromLCMSIonAnalysis {

  public static final String OPTION_INPUT_FILES = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_MIN_INTENSITY_THRESHOLD = "n";
  public static final String OPTION_MIN_TIME_THRESHOLD = "t";
  public static final String OPTION_MIN_SNR_THRESHOLD = "s";
  public static final String OPTION_GET_IONS_SUPERSET = "f";
  public static final String OPTION_JSON_FORMAT = "j";
  public static final String OPTION_MIN_OF_REPLICATES = "t";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILES)
        .argName("input file")
        .desc("The input files containing molecular hit results in the IonAnalysisInterchangeModel serialized object " +
            "format for every positive replicate well from the same lcms mining run.")
        .hasArgs()
        .valueSeparator(',')
        .required()
        .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output file")
        .desc("The output file to write validated inchis to")
        .hasArg().required()
        .longOpt("output-file")
    );
    add(Option.builder(OPTION_MIN_INTENSITY_THRESHOLD)
        .argName("min intensity threshold")
        .desc("The min intensity threshold")
        .hasArg()
        .longOpt("min-intensity-threshold")
    );
    add(Option.builder(OPTION_MIN_TIME_THRESHOLD)
        .argName("min time threshold")
        .desc("The min time threshold")
        .hasArg()
        .longOpt("min-time-threshold")
    );
    add(Option.builder(OPTION_MIN_SNR_THRESHOLD)
        .argName("min snr threshold")
        .desc("The min snr threshold")
        .hasArg()
        .longOpt("min-snr-threshold")
    );
    add(Option.builder(OPTION_GET_IONS_SUPERSET)
        .argName("ions superset")
        .desc("A run option on all the ionic variant files on a single replicate run")
        .longOpt("ions-superset")
    );
    add(Option.builder(OPTION_JSON_FORMAT)
        .argName("json format")
        .desc("Output the result in the IonAnalysisInterchangeModel json format. If not, just output a list of inchis")
        .longOpt("json-format")
    );
    add(Option.builder(OPTION_GET_IONS_SUPERSET)
        .argName("ions superset")
        .desc("A run option on all the ionic variant files on a single replicate run")
        .longOpt("ions-superset")
    );
    add(Option.builder(OPTION_MIN_OF_REPLICATES)
        .argName("min of replicates")
        .desc("Get the min intensities, SNR and times of multiple replicates")
        .longOpt("ions-superset")
    );
  }};

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This module takes as inputs LCMS analysis results in the form of IonAnalysisInterchangeModel serialized object files ",
          "for every positive replicate vs negative controls. Based on these, it identifies inchis that are hits on all the ",
          "replicates and writes them to an output file."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  /**
   * This function is used to print the model either as a json or as a list of inchis
   * @param fileName The name of file
   * @param jsonFormat Whether it needs to be outputted in a json format
   * @param model The model that is being written
   * @throws IOException
   */
  public static void printToFile(String fileName, Boolean jsonFormat, IonAnalysisInterchangeModel model) throws IOException {
    if (jsonFormat) {
      model.writeToJsonFile(new File(fileName));
    } else {
      try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(fileName))) {
        for (String inchi : model.getAllInchis()) {
          predictionWriter.append(inchi);
          predictionWriter.newLine();
        }
      }
    }
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
      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));

    List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));

    IonAnalysisInterchangeModel model;

    if (cl.hasOption(OPTION_GET_IONS_SUPERSET)) {
      model = IonAnalysisInterchangeModel.getSupersetOfIonicVariants(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          minSnrThreshold,
          minIntensityThreshold,
          minTimeThreshold);
    } else if (cl.hasOption(OPTION_MIN_OF_REPLICATES)) {
      Function<List<Double>, Pair<Double, Boolean>> intensityFilterFunction = (List<Double> listOfIntensities) ->
          Pair.of(listOfIntensities.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal)), true);

      Function<List<Double>, Pair<Double, Boolean>> snrFilterFunction = (List<Double> listOfSnrs) ->
          Pair.of(listOfSnrs.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal)), true);

      Function<List<Double>, Pair<Double, Boolean>> timeFilterFunction = (List<Double> listOfTimes) ->
          Pair.of(listOfTimes.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal)), true);

      model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          intensityFilterFunction, snrFilterFunction,
          timeFilterFunction);
    } else {
      Function<List<Double>, Pair<Double, Boolean>> intensityFilterFunction = (List<Double> listOfIntensities) -> {
        for (Double val : listOfIntensities) {
          if (val < minIntensityThreshold) {
            return Pair.of(0.0, false);
          }
        }

        // If all the intensities for all the replicates pass the threshold, then keep the molecule in the output
        /// ion model and set the intensities to a placeholder intensities, in this case, the first element's intensities.
        return Pair.of(listOfIntensities.get(0), true);
      };

      Function<List<Double>, Pair<Double, Boolean>> snrFilterFunction = (List<Double> listOfSnrs) -> {
        for (Double val : listOfSnrs) {
          if (val < minSnrThreshold) {
            return Pair.of(0.0, false);
          }
        }

        // If all the snrs for all the replicates pass the threshold, then keep the molecule in the output
        /// ion model and set the snr to a placeholder snr, in this case, the first element's snr.
        return Pair.of(listOfSnrs.get(0), true);
      };

      Function<List<Double>, Pair<Double, Boolean>> timeFilterFunction = (List<Double> listOfTimes) -> {
        for (Double val : listOfTimes) {
          if (val < minTimeThreshold) {
            return Pair.of(0.0, false);
          }
        }

        // If all the times for all the replicates pass the threshold, then keep the molecule in the output
        /// ion model and set the times to a placeholder times, in this case, the first element's times.
        return Pair.of(listOfTimes.get(0), true);
      };

      model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          intensityFilterFunction, snrFilterFunction, timeFilterFunction);
    }

    printToFile(cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT), model);
  }
}
