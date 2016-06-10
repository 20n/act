package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs analysis on generated prediction corpus
 */
public class PredictionCorpusAnalyzer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final String OPTION_CORPUS_PATH = "c";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
          "This class is used to analyze an already-generated prediction corpus.  The corpus is read in from" +
                  "file, and basic statistics about it are generated.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CORPUS_PATH)
            .argName("corpus file path")
            .desc("The path to the prediction corpus file.")
            .hasArg()
            .longOpt("corpus-path")
            .required()
    );
    add(Option.builder(OPTION_HELP)
            .argName("help")
            .desc("Prints this help message.")
            .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {

    // Build command line parser
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
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Set filenames
    String corpusFile = cl.getOptionValue(OPTION_CORPUS_PATH);

    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);

    LOGGER.info("Total predictions: %d", predictionCorpus.countPredictions(prediction -> true));
    LOGGER.info("Predictions with no matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() == 0));
    LOGGER.info("Predictions with some matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() > 0));
    LOGGER.info("Predictions with reaction that matches RO: %d",
            predictionCorpus.countPredictions(prediction -> prediction.matchesRo()));

    LOGGER.info("L2ExpansionDriver complete!");
  }
}
