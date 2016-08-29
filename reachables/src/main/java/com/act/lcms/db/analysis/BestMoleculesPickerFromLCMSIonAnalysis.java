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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BestMoleculesPickerFromLCMSIonAnalysis {

  public static final String OPTION_INPUT_FILES = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_MIN_INTENSITY_THRESHOLD = "n";
  public static final String OPTION_MIN_TIME_THRESHOLD = "t";
  public static final String OPTION_MIN_SNR_THRESHOLD = "s";
  public static final String OPTION_GET_IONS_SUPERSET = "f";
  public static final String OPTION_GET_CHEMICAL_STATISTICS = "c";

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
    add(Option.builder(OPTION_GET_CHEMICAL_STATISTICS)
        .argName("get chemical statistics")
        .desc("Get chemicals from input file")
        .hasArg()
        .longOpt("chemical-statistics")
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

  public static Set<String> readChemicalsFromFile(File in) throws IOException {
    FileReader fileReader = new FileReader(in);
    Set<String> inchis = new HashSet<>();

    try (BufferedReader reader = new BufferedReader(fileReader)) {
      String inchi = null;
      while((inchi = reader.readLine()) != null) {
        inchis.add(inchi);
      }
    }

    return inchis;
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

    if (cl.hasOption(OPTION_GET_CHEMICAL_STATISTICS)) {
      Set<String> inchis = readChemicalsFromFile(new File(cl.getOptionValue(OPTION_GET_CHEMICAL_STATISTICS)));

      for (String file : positiveReplicateResults) {
        System.out.println(file);

        IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
        model.loadResultsFromFile(new File(file));

        for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : model.getResults()) {
          for (IonAnalysisInterchangeModel.HitOrMiss hitOrMiss : resultForMZ.getMolecules()) {
            if (inchis.contains(hitOrMiss.getInchi())) {
              System.out.println(String.format("Ion: %s", hitOrMiss.getIon()));
              System.out.println(String.format("Intensity: %s", hitOrMiss.getIntensity()));
              System.out.println(String.format("SNR: %s", hitOrMiss.getSnr()));
              System.out.println(String.format("Time: %s", hitOrMiss.getTime()));
            }
          }
        }
      }

      return;
    }

    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));

    Set<String> inchis = cl.hasOption(OPTION_GET_IONS_SUPERSET) ?
        IonAnalysisInterchangeModel.getSupersetOfIonicVariants(positiveReplicateResults, minSnrThreshold,
            minIntensityThreshold, minTimeThreshold) :
        IonAnalysisInterchangeModel.getAllMoleculeHitsFromMultiplePositiveReplicateFiles(
        positiveReplicateResults, minSnrThreshold, minIntensityThreshold, minTimeThreshold);

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)))) {
      for (String inchi : inchis) {
        predictionWriter.append(inchi);
        predictionWriter.newLine();
      }
    }
  }
}
