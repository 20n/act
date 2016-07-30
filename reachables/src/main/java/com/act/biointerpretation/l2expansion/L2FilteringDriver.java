package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class L2FilteringDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2FilteringDriver.class);

  private static final String OPTION_INPUT_CORPUS = "i";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_CHEMICAL_FILTER = "c";
  private static final String OPTION_REACTION_FILTER = "r";
  private static final String OPTION_DB_LOOKUP = "d";
  private static final String OPTION_LOOKUP_TYPES = "L";
  private static final String OPTION_SPLIT_BY_RO = "s";
  private static final String OPTION_FILTER_SUBSTRATES = "S";
  private static final String OPTION_HELP = "h";

  private static final String APPLY_FILTER_POSITIVE = "1";
  private static final String APPLY_FILTER_NEGATED = "0";
  private static final String LOOKUP_REACTIONS = "r";
  private static final String LOOKUP_CHEMICALS = "c";

  public static final String HELP_MESSAGE =
      "This class is used to filter an L2PredictionCorpus. An initial corpus is read in from file, processed based on" +
          "the selected options, and then the result is printed in json format.";

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
            "were not all found. This step must either be run on a corpus that already has chemical DB info, or " +
            "supplied in conjunction with the db-lookup option to populate the chemical info fields before filtering.")
        .hasArg()
        .longOpt("chemical-db-filter")
    );
    add(Option.builder(OPTION_REACTION_FILTER)
        .argName("reaction db filter")
        .desc("Use the reaction filter.  Input the value " + APPLY_FILTER_POSITIVE + " to keep predictions which " +
            "match a reaction in the DB, or " + APPLY_FILTER_NEGATED + " to keep those which don't. This step must " +
            "either be run on a corpus that already has reaction DB info, supplied in conjunction with the db-lookup " +
            "option to populate the reaction info fields before filtering.")
        .hasArg()
        .longOpt("reaction-db-filter")
    );
    add(Option.builder(OPTION_DB_LOOKUP)
        .argName("db name")
        .desc("Mongo DB to use for lookups; needed only if population of chemical and reaction DB info is desired..")
        .hasArg()
        .longOpt("db-name"));
    add(Option.builder(OPTION_LOOKUP_TYPES)
        .argName("db lookup types")
        .desc("This argument specifies which lookup types to use. Use " + LOOKUP_CHEMICALS + " for chemical lookups, " +
            LOOKUP_REACTIONS + " for reaction lookups, or both. These lookups compare the predictions against our DB " +
            "and populate the chemical and reaction fields of the L2Predictions accordingly.")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("db-lookup-types"));
    add(Option.builder(OPTION_SPLIT_BY_RO)
        .argName("split by ro")
        .desc("If this argument is selected, the input corpus is read in, split up by ro, and written out into a " +
            "different output file for each ro found in the corpus. The files have the ro id appended to the end of " +
            "their names to distinguish them.")
        .longOpt("split-by-ro"));
    add(Option.builder(OPTION_FILTER_SUBSTRATES)
        .argName("filter substrates path")
        .desc("If this argument is selected, a list of substrates to keep is fed in, and the corpus is filtered " +
            "to preserve only predictions with substrates among that list.")
        .hasArg()
        .longOpt("filter-substrates"));
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
    outputFile.createNewFile();
    if (outputFile.isDirectory()) {
      LOGGER.error("Output file is directory.");
      System.exit(1);
    }

    LOGGER.info("Reading corpus from file.");
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);
    LOGGER.info("Read in corpus with %d predictions.", predictionCorpus.getCorpus().size());
    LOGGER.info("Corpus has %d distinct substrates.", predictionCorpus.getUniqueSubstrateInchis().size());

    if (cl.hasOption(OPTION_FILTER_SUBSTRATES)) {
      LOGGER.info("Filtering by substrates.");
      File substratesFile = new File(cl.getOptionValue(OPTION_FILTER_SUBSTRATES));
      L2InchiCorpus inchis = new L2InchiCorpus();
      inchis.loadCorpus(substratesFile);
      Set<String> inchiSet = new HashSet<String>();
      inchiSet.addAll(inchis.getInchiList());

      predictionCorpus = predictionCorpus.applyFilter(
          prediction -> inchiSet.containsAll(prediction.getSubstrateInchis()));

      predictionCorpus.writePredictionsToJsonFile(outputFile);
      LOGGER.info("Done writing filtered corpus to file.");
      return;
    }

    if (cl.hasOption(OPTION_SPLIT_BY_RO)) {
      LOGGER.info("Splitting corpus into distinct corpuses for each ro.");
      Map<String, L2PredictionCorpus> corpusMap = predictionCorpus.splitCorpus(prediction -> prediction.getProjectorName());

      for (Map.Entry<String, L2PredictionCorpus> entry : corpusMap.entrySet()) {
        String fileName = cl.getOptionValue(OPTION_OUTPUT_PATH) + "." + entry.getKey();
        File oneOutputFile = new File(fileName);
        entry.getValue().writePredictionsToJsonFile(oneOutputFile);
      }
      LOGGER.info("Done writing split corpuses to file.");
      return;
    }


    if (cl.hasOption(OPTION_SPLIT_BY_RO)) {
      LOGGER.info("Splitting corpus into distinct corpuses for each ro.");
      Map<String, L2PredictionCorpus> corpusMap = predictionCorpus.splitCorpus(prediction -> prediction.getProjectorName());

      for (Map.Entry<String, L2PredictionCorpus> entry : corpusMap.entrySet()) {
        String fileName = cl.getOptionValue(OPTION_OUTPUT_PATH) + "." + entry.getKey();
        File oneOutputFile = new File(fileName);
        entry.getValue().writePredictionsToJsonFile(oneOutputFile);
      }
      LOGGER.info("Done writing split corpuses to file.");
      return;
    }

    predictionCorpus = runDbLookups(cl, predictionCorpus, opts);

    LOGGER.info("Applying filters.");
    predictionCorpus = applyFilter(predictionCorpus, ALL_CHEMICALS_IN_DB, cl, OPTION_CHEMICAL_FILTER);
    predictionCorpus = applyFilter(predictionCorpus, REACTION_MATCHES_DB, cl, OPTION_REACTION_FILTER);
    LOGGER.info("Filtered corpus has %d predictions.", predictionCorpus.getCorpus().size());

    LOGGER.info("Printing final corpus.");
    predictionCorpus.writePredictionsToJsonFile(outputFile);

    LOGGER.info("L2FilteringDriver complete!.");
  }

  private static L2PredictionCorpus runDbLookups(CommandLine cl, L2PredictionCorpus predictionCorpus, Options opts)
      throws IOException {

    if (cl.hasOption(OPTION_DB_LOOKUP)) {

      if (cl.hasOption(OPTION_LOOKUP_TYPES)) {

        LOGGER.info("Instantiating mongoDB.");
        MongoDB mongoDB = new MongoDB("localhost", 27017, cl.getOptionValue(OPTION_DB_LOOKUP));

        String[] lookupOptions = cl.getOptionValues(OPTION_LOOKUP_TYPES);
        Set<String> lookupSet = new HashSet<>();
        for (String option : lookupOptions) {
          if (!option.equals(LOOKUP_CHEMICALS) && !option.equals(LOOKUP_REACTIONS)) {
            LOGGER.error("Invalid lookup option supplied: %s", option);
            HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
            System.exit(1);
          }
          lookupSet.add(option);
        }

        if (lookupSet.contains(LOOKUP_CHEMICALS)) {
          LOGGER.info("Looking up chemicals in DB.");
          predictionCorpus = predictionCorpus.applyTransformation(new ChemicalsTransformer(mongoDB));
        }
        if (lookupSet.contains(LOOKUP_REACTIONS)) {
          LOGGER.info("Looking up reactions in DB.");
          predictionCorpus = predictionCorpus.applyTransformation(new ReactionsTransformer(mongoDB));
        }

      } else {
        LOGGER.warn("Mongo DB instantiated but lookup option not selected.");
      }
    }
    return predictionCorpus;
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
