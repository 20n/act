package com.act.biointerpretation.l2expansion;

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
  private static final String OPTION_TSV_PATH = "o";

  public static final String HELP_MESSAGE =
          "This class is used to analyze an already-generated prediction corpus.  The corpus is read in from" +
                  "file, and basic statistics about it are generated.  If desired, the corpus can also be written" +
                  "to a TSV file for outside processing.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CORPUS_PATH)
            .argName("corpus file path")
            .desc("The path to the prediction corpus file.")
            .hasArg()
            .longOpt("corpus-path")
            .required()
    );
    add(Option.builder(OPTION_TSV_PATH)
            .argName("tsv output file path")
            .desc("The path to which to write out a TSV file, if desired.")
            .hasArg()
            .longOpt("tsv-output-path")
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

    // Build command line parser.
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

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Get prediction corpus from file.
    String corpusFile = cl.getOptionValue(OPTION_CORPUS_PATH);
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);

    // Print summary statistics for the corpus.
    LOGGER.info("Total predictions: %d", predictionCorpus.countPredictions(prediction -> true));
    LOGGER.info("Predictions with no matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() == 0));
    LOGGER.info("Predictions with some matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() > 0));
    LOGGER.info("Predictions with reaction that matches RO: %d",
            predictionCorpus.countPredictions(prediction -> prediction.matchesRo()));

    // Write the corpus to TSV file, if option is selected.
    if (cl.hasOption(OPTION_TSV_PATH)) {
      LOGGER.info("Writing prediction corpus to TSV file for further processing.");
      String tsvFile = cl.getOptionValue(OPTION_TSV_PATH);
      predictionCorpus.writePredictionsToTSVFile(tsvFile);
    }

    LOGGER.info("L2ExpansionDriver complete!");
  }
}
