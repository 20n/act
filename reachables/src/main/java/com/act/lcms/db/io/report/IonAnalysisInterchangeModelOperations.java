package com.act.lcms.db.io.report;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IonAnalysisInterchangeModelOperations {
  private static final Logger LOGGER = LogManager.getFormatterLogger(IonAnalysisInterchangeModelOperations.class);
  private static final String OPTION_INPUT_FILE = "i";
  private static final String OPTION_OUTPUT_FILE = "o";
  private static final String OPTION_LOG_DISTRIBUTION = "l";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This module is used for performing operations on the IonAnalysisInterchangeModelOperations model like computing ",
      "the frequency distribution of intensities or SNR values over molecule hits."}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_INPUT_FILE)
          .argName("input-file")
          .desc("This option is for taking in an input IonAnalysisInterchangeModel file")
          .hasArg()
          .longOpt("input-file")
          .required()
      );
      add(Option.builder(OPTION_OUTPUT_FILE)
          .argName("output-file")
          .desc("This option is for the output file")
          .hasArg()
          .longOpt("output-file")
          .required()
      );
      add(Option.builder(OPTION_LOG_DISTRIBUTION)
          .argName("log-distribution")
          .desc("This option is for calculating the log distribution of a metric (SNR, Intensity, Time) over molecule" +
              "counts in the model")
          .hasArg()
          .longOpt("log-distribution")
          .required()
      );
    }
  };

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws IOException {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(IonAnalysisInterchangeModelOperations.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(IonAnalysisInterchangeModelOperations.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption(OPTION_LOG_DISTRIBUTION)) {
      IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
      model.loadResultsFromFile(new File(cl.getOptionValue(OPTION_INPUT_FILE)));
      Map<Pair<Double, Double>, Integer> rangeToCount = model.computeLogFrequencyDistributionOfMoleculeCountToMetric(
          IonAnalysisInterchangeModel.METRIC.valueOf(cl.getOptionValue(OPTION_LOG_DISTRIBUTION)));

      try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File(OPTION_OUTPUT_FILE)))) {
        for (Map.Entry<Pair<Double, Double>, Integer> entry : rangeToCount.entrySet()) {
          String value = String.format("%f,%d", entry.getKey().getLeft(), entry.getValue());
          predictionWriter.write(value);
          predictionWriter.newLine();
        }
      }
    }
  }
}
