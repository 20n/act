package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class BestMoleculesPickerFromLCMSIonAnalysis {

  public static final Boolean DO_NOT_THROW_OUT_MOLECULE = true;
  public static final Boolean THROW_OUT_MOLECULE = false;
  public static final Integer TIME_TOLERANCE_IN_SECONDS = 5;
  public static final Integer REPRESENTATIVE_INDEX = 0;
  public static final Double LOWEST_POSSIBLE_VALUE_FOR_METRIC = 0.0;
  public static final String OPTION_INPUT_FILES = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_MIN_INTENSITY_THRESHOLD = "n";
  public static final String OPTION_MIN_TIME_THRESHOLD = "t";
  public static final String OPTION_MIN_SNR_THRESHOLD = "s";
  public static final String OPTION_GET_IONS_SUPERSET = "f";
  public static final String OPTION_JSON_FORMAT = "j";
  public static final String OPTION_MIN_OF_REPLICATES = "m";
  public static final String OPTION_FILTER_BY_THRESHOLD = "p";
  public static final String OPTION_FILTER_BY_IONS = "k";

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
        .desc("Output the result in the IonAnalysisInterchangeModel json format. If not, output a list of inchis, one per line.")
        .longOpt("json-format")
    );
    add(Option.builder(OPTION_MIN_OF_REPLICATES)
        .argName("min of replicates")
        .desc("Get the min intensities, SNR and times of multiple replicates")
        .longOpt("ions-superset")
    );
    add(Option.builder(OPTION_FILTER_BY_THRESHOLD)
        .argName("filter by threshold")
        .desc("Filter files by threshold amounts")
        .longOpt("filter-threshold")
    );
    add(Option.builder(OPTION_FILTER_BY_IONS)
        .argName("filter by ions")
        .desc("Filter files by ion")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("filter-by-ions")
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
   * @param jsonFormat Whether it needs to be outputted in a json format or a a list of inchis, one per line.
   * @param model The model that is being written
   * @throws IOException
   */
  public static void printInchisAndIonsToFile(IonAnalysisInterchangeModel model, String fileName, Boolean jsonFormat) throws IOException {
    if (jsonFormat) {
      model.writeToJsonFile(new File(fileName));
    } else {
      try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(fileName))) {

        TreeMap<Double, Pair<String, String>> massChargeToChemicalAndIon = new TreeMap<>();
        for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : model.getResults()) {
          for (IonAnalysisInterchangeModel.HitOrMiss molecule : resultForMZ.getMolecules()) {
            massChargeToChemicalAndIon.put(resultForMZ.getMz(), Pair.of(molecule.getInchi(), molecule.getIon()));
          }
        }

        for (Map.Entry<Double, Pair<String, String>> entry : massChargeToChemicalAndIon.entrySet()) {
          predictionWriter.append(String.format("MZ: %s, Inchi: %s, Ion: %s", entry.getKey(), entry.getValue().getLeft(), entry.getValue().getRight()));
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

    List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));

    if (cl.hasOption(OPTION_MIN_OF_REPLICATES)) {
      Function<Triple<List<Double>, List<Double>, List<Double>>, Pair<Triple<Double, Double, Double>, Boolean>>
          filterAndTransformFunction = (Triple<List<Double>, List<Double>, List<Double>> listOfPeakStats) -> {

        List<Double> intensityValues = listOfPeakStats.getLeft();
        List<Double> snrValues = listOfPeakStats.getMiddle();
        List<Double> timeValues = listOfPeakStats.getRight();

        Double minTime = timeValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));
        Double maxTime = timeValues.stream().reduce(Double.MIN_VALUE, (accum, newVal) -> Math.max(accum, newVal));

        if (maxTime - minTime < TIME_TOLERANCE_IN_SECONDS) {
          Double minIntensity = intensityValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));

          // ASSUMPTION: We assume that the list of SNR, Intensity and Time values are ordered similarly by replicates.
          Integer indexOfMinIntensityReplicate = intensityValues.indexOf(minIntensity);

          // The SNR and Time values will be the copy of the replicate with the lowest intensity value.
          return Pair.of(
              Triple.of(minIntensity, snrValues.get(indexOfMinIntensityReplicate),
                  timeValues.get(indexOfMinIntensityReplicate)), DO_NOT_THROW_OUT_MOLECULE);
        } else {
          // TODO: We can just throw out such molecules.
          return Pair.of(
              Triple.of(
                  LOWEST_POSSIBLE_VALUE_FOR_METRIC,
                  LOWEST_POSSIBLE_VALUE_FOR_METRIC,
                  LOWEST_POSSIBLE_VALUE_FOR_METRIC),
              DO_NOT_THROW_OUT_MOLECULE);
        }
      };

      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          filterAndTransformFunction);

      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
      return;
    }

    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));

    if (cl.hasOption(OPTION_GET_IONS_SUPERSET)) {
      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.getSupersetOfIonicVariants(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          minSnrThreshold,
          minIntensityThreshold,
          minTimeThreshold);

      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
      return;
    }

    if (cl.hasOption(OPTION_FILTER_BY_THRESHOLD)) {
      Function<Triple<List<Double>, List<Double>, List<Double>>, Pair<Triple<Double, Double, Double>, Boolean>>
          filterAndTransformFunction = (Triple<List<Double>, List<Double>, List<Double>> listOfPeakStats) -> {

        List<Double> intensityValues = listOfPeakStats.getLeft();
        List<Double> snrValues = listOfPeakStats.getMiddle();
        List<Double> timeValues = listOfPeakStats.getRight();

        if (intensityValues.size() > 1 && snrValues.size() > 1 && timeValues.size() > 1) {
          throw new RuntimeException("This filter is meant for analysis for only one replicate.");
        }

        Double intensity = intensityValues.get(REPRESENTATIVE_INDEX);
        Double snr = snrValues.get(REPRESENTATIVE_INDEX);
        Double time = timeValues.get(REPRESENTATIVE_INDEX);

        if (intensity > minIntensityThreshold && snr > minSnrThreshold && time > minTimeThreshold) {
          return Pair.of(Triple.of(intensity, snr, time), DO_NOT_THROW_OUT_MOLECULE);
        } else {
          return Pair.of(Triple.of(intensity, snr, time), THROW_OUT_MOLECULE);
        }
      };

      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          filterAndTransformFunction);

      if (cl.hasOption(OPTION_FILTER_BY_IONS)) {
        model.filterByIons(new HashSet<>(Arrays.asList(cl.getOptionValues(OPTION_FILTER_BY_IONS))));
      }

      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
    }
  }
}
