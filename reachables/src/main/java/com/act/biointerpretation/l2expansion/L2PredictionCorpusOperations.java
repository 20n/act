package com.act.biointerpretation.l2expansion;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class L2PredictionCorpusOperations {
  private static final Logger LOGGER = LogManager.getFormatterLogger(L2PredictionCorpusOperations.class);
  private static final String OPTION_GET_LIST_OF_PRODUCTS = "f";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_INPUT_PATH = "i";

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This module is used to perform operations on L2PredictionCorpus results, like getting products from the all ",
      "substrates of the model"
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_OUTPUT_PATH)
          .argName("output file path")
          .desc("The path to the file to which to write the json file of predicted reactions.")
          .hasArg()
          .longOpt("output-file-path")
          .required(true)
      );
      add(Option.builder(OPTION_INPUT_PATH)
          .argName("input file path")
          .desc("The input path of the prediction corpus")
          .hasArg()
          .longOpt("input-file-path")
          .required(true)
      );
      add(Option.builder(OPTION_GET_LIST_OF_PRODUCTS)
          .argName("get list of products from prediction corpus")
          .desc("The list of products from input prediction corpus file")
          .hasArg()
          .longOpt("list-of-products")
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
      HELP_FORMATTER.printHelp(L2PredictionCorpusOperations.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(L2PredictionCorpusOperations.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption(OPTION_GET_LIST_OF_PRODUCTS)) {
      L2PredictionCorpus corpus = L2PredictionCorpus.readPredictionsFromJsonFile(
          new File(cl.getOptionValue(OPTION_GET_LIST_OF_PRODUCTS)));
      corpus.writePredictionsAsInchiList(new File(cl.getOptionValue(OPTION_OUTPUT_PATH)));
    }
  }
}
