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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class L2FilteringDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2FilteringDriver.class);

  private static final String OPTION_INPUT_CORPUS = "i";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_CHEMICAL_FILTER = "c";
  private static final String OPTION_REACTION_FILTER = "r";
  private static final String OPTION_HELP = "h";

  private static final String APPLY_FILTER_POSITIVE = "1";
  private static final String APPLY_FILTER_NEGATED = "0";

  public static final String HELP_MESSAGE =
      "This class is used to filter an L2PredictionCorpus. It contains two filters, each of which can be applied " +
          "as is, or negated and then applied, to the corpus.  After all selected filtering steps are performed, the " +
          "resulting corpus is printed in json format.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_CORPUS)
        .argName("input corpus path")
        .desc("The absolute path to the input prediction corpus.")
        .hasArg()
        .longOpt("input-corpus-path")
        .required(true)
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output path")
        .desc("The path to which to write the output.")
        .hasArg()
        .longOpt("output-path")
        .required(true)
    );
    add(Option.builder(OPTION_CHEMICAL_FILTER)
        .argName("chemical db filter")
        .desc("Use the chemical filter.  Input the value " + APPLY_FILTER_POSITIVE + " to keep predictions whose " +
            "chemicals were all found in the DB, or " + APPLY_FILTER_NEGATED + " to keep those whose chemicals " +
            "were not all found.")
        .hasArg()
        .longOpt("chemical-db-filter")
    );
    add(Option.builder(OPTION_REACTION_FILTER)
        .argName("reaction db filter")
        .desc("Use the reaction filter.  Input the value " + APPLY_FILTER_POSITIVE + " to keep predictions which " +
            "match a reaction in the DB, or " + APPLY_FILTER_NEGATED + " to keep those which don't.")
        .hasArg()
        .longOpt("reaction-db-filter")
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

  private static final Predicate<L2Prediction> ALL_CHEMICALS_IN_DB = prediction ->
      prediction.getProductIds().size() == prediction.getProducts().size() &&
          prediction.getSubstrateIds().size() == prediction.getSubstrates().size();

  private static final Predicate<L2Prediction> REACTION_MATCHES_DB = prediction -> prediction.getReactionCount() > 0;

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
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    checkFilterOptionIsValid(OPTION_CHEMICAL_FILTER, cl);
    checkFilterOptionIsValid(OPTION_REACTION_FILTER, cl);

    // Get corpus files.
    File corpusFile = new File(cl.getOptionValue(OPTION_INPUT_CORPUS));
    if (!corpusFile.exists()) {
      LOGGER.error("Input corpus file does not exist.");
      return;
    }
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));

    LOGGER.info("Reading corpus from file.");
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);
    LOGGER.info("Read in corpus with %d predictions.", predictionCorpus.getCorpus().size());

    LOGGER.info("Applying filters.");
    predictionCorpus = applyFilter(predictionCorpus, ALL_CHEMICALS_IN_DB, cl, OPTION_CHEMICAL_FILTER);
    predictionCorpus = applyFilter(predictionCorpus, REACTION_MATCHES_DB, cl, OPTION_REACTION_FILTER);
    LOGGER.info("Filtered corpus has %d predictions.", predictionCorpus.getCorpus().size());

    LOGGER.info("Printing final corpus.");
    predictionCorpus.writePredictionsToJsonFile(outputFile);

    LOGGER.info("L2FilteringDriver complete!.");
  }

  private static void checkFilterOptionIsValid(String filterOption, CommandLine cl) {
    if (cl.hasOption(filterOption)) {
      if (cl.getOptionValue(filterOption).equals(APPLY_FILTER_POSITIVE)
          || cl.getOptionValue(filterOption).equals(APPLY_FILTER_NEGATED)) {
        return;
      } else {
        LOGGER.error("Option %s value not valid.  Must receive value %s or %s",
            filterOption, APPLY_FILTER_POSITIVE, APPLY_FILTER_NEGATED);
        throw new IllegalArgumentException("Command line value invalid.");
      }
    }
  }

  private static L2PredictionCorpus applyFilter(L2PredictionCorpus corpus,
                                                Predicate<L2Prediction> filter,
                                                CommandLine cl,
                                                String filterOption) throws IOException {
    if (cl.hasOption(filterOption)) {
      if (cl.getOptionValue(filterOption).equals(APPLY_FILTER_NEGATED)) {
        return corpus.applyFilter(filter.negate());
      }
      return corpus.applyFilter(filter);
    }
    return corpus;
  }
}
