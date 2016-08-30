package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.ws.rs.HEAD;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  public static final String HEADER_INCHI = "Inchi";
  public static final String HEADER_MASS = "Monoisotopic Mass";
  public static final String HEADER_MZ = "MZ";
  public static final String HEADER_ION = "Ion";

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

  public static final List<String> OUTPUT_TSV_HEADER_FIELDS = new ArrayList<String>() {{
    add(HEADER_INCHI);
    add(HEADER_ION);
    add(HEADER_MASS);
    add(HEADER_MZ);
  }};

  /**
   * This function is used to print the model either as a json or as a TSV file containing relevant statistics.
   * @param fileName The name of file
   * @param jsonFormat Whether it needs to be outputted in a json format or a a list of inchis, one per line.
   * @param model The model that is being written
   * @throws IOException
   */
  public static void printInchisAndIonsToFile(IonAnalysisInterchangeModel model, String fileName, Boolean jsonFormat) throws IOException {
    if (jsonFormat) {
      model.writeToJsonFile(new File(fileName));
    } else {
      List<String> header = new ArrayList<>();
      header.addAll(OUTPUT_TSV_HEADER_FIELDS);

      TSVWriter<String, String> writer = new TSVWriter<>(header);
      writer.open(new File(fileName));

      for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : model.getResults()) {
        for (IonAnalysisInterchangeModel.HitOrMiss molecule : resultForMZ.getMolecules()) {
          Map<String, String> row = new HashMap<>();
          row.put(HEADER_INCHI, molecule.getInchi());
          row.put(HEADER_ION, molecule.getIon());
          row.put(HEADER_MASS, MassCalculator.calculateMass(molecule.getInchi()).toString());
          row.put(HEADER_MZ, resultForMZ.getMz().toString());
          writer.append(row);
          writer.flush();
        }
      }

      writer.close();
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
      Function<List<IonAnalysisInterchangeModel.HitOrMiss>, Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean>>
          filterAndTransformFunction = (List<IonAnalysisInterchangeModel.HitOrMiss> listOfReplicateMolecules) -> {

        List<Double> intensityValues = listOfReplicateMolecules.stream().map(molecule -> molecule.getIntensity()).collect(Collectors.toList());
        List<Double> snrValues = listOfReplicateMolecules.stream().map(molecule -> molecule.getSnr()).collect(Collectors.toList());
        List<Double> timeValues = listOfReplicateMolecules.stream().map(molecule -> molecule.getTime()).collect(Collectors.toList());

        Double minTime = timeValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));
        Double maxTime = timeValues.stream().reduce(Double.MIN_VALUE, (accum, newVal) -> Math.max(accum, newVal));

        IonAnalysisInterchangeModel.HitOrMiss result = new IonAnalysisInterchangeModel.HitOrMiss();
        result.setInchi(listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getInchi());
        result.setIon(listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getIon());
        result.setPlot("NIL");

        if (maxTime - minTime < TIME_TOLERANCE_IN_SECONDS) {
          Double minIntensity = intensityValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));

          // ASSUMPTION: We assume that the list of SNR, Intensity and Time values are ordered similarly by replicates.
          Integer indexOfMinIntensityReplicate = intensityValues.indexOf(minIntensity);

          // The SNR and Time values will be the copy of the replicate with the lowest intensity value.
          result.setSnr(snrValues.get(indexOfMinIntensityReplicate));
          result.setIntensity(minIntensity);
          result.setTime(timeValues.get(indexOfMinIntensityReplicate));

          return Pair.of(result, DO_NOT_THROW_OUT_MOLECULE);
        } else {
          // TODO: We can just throw out such molecules.
          result.setSnr(LOWEST_POSSIBLE_VALUE_FOR_METRIC);
          result.setIntensity(LOWEST_POSSIBLE_VALUE_FOR_METRIC);
          result.setTime(LOWEST_POSSIBLE_VALUE_FOR_METRIC);

          return Pair.of(result, DO_NOT_THROW_OUT_MOLECULE);
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

      final Set<String> ions;
      if (cl.hasOption(OPTION_FILTER_BY_IONS)) {
        ions = new HashSet<>(Arrays.asList(cl.getOptionValues(OPTION_FILTER_BY_IONS)));
      } else {
        ions = new HashSet<>();
      }

      Function<List<IonAnalysisInterchangeModel.HitOrMiss>, Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean>>
          filterAndTransformFunction = (List<IonAnalysisInterchangeModel.HitOrMiss> listOfReplicateMolecules) -> {

        if (listOfReplicateMolecules.size() > 1) {
          throw new RuntimeException("This filter is meant for analysis for only one replicate.");
        }

        Double intensity = listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getIntensity();
        Double snr = listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getSnr();
        Double time = listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getTime();
        String ion = listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getIon();

        IonAnalysisInterchangeModel.HitOrMiss molecule = new IonAnalysisInterchangeModel.HitOrMiss(
            listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getInchi(),
            listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getIon(),
            snr,
            time,
            intensity,
            listOfReplicateMolecules.get(REPRESENTATIVE_INDEX).getPlot()
        );

        if (intensity > minIntensityThreshold && snr > minSnrThreshold && time > minTimeThreshold &&
            (ions.size() == 0 || ions.contains(ion))) {
          return Pair.of(molecule, DO_NOT_THROW_OUT_MOLECULE);
        } else {
          return Pair.of(molecule, THROW_OUT_MOLECULE);
        }
      };

      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
          filterAndTransformFunction);

      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
    }
  }
}
