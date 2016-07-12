package com.act.biointerpretation.sars;

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
import java.util.ArrayList;
import java.util.List;

public class SarGenerationDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarGenerationDriver.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_CARBON_THRESHOLD = "t";
  private static final String OPTION_LIMIT = "l";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class is used to generate SARs from an instance of the MongoDB. It groups seq entries together based on " +
          "sequence identity and then tries to build a SAR for each group of reactions catalyzed by the same enzyme.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .type(String.class)
        .required(true)
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output file path")
        .desc("The absolute path to the file to which to write the json file of the sar corpus.")
        .hasArg()
        .longOpt("output-file-path")
        .required(true)
    );
    add(Option.builder(OPTION_CARBON_THRESHOLD)
        .argName("carbon threshold")
        .desc("The minimum ratio of the number of carbons in a substructure to the average number of carbons in the " +
            "substrates of the reactions. If the actual ratio is below this number, we consider the SAR invalid.")
        .hasArg()
        .longOpt("carbon-thresh")
        .type(Double.class)
    );
    add(Option.builder(OPTION_LIMIT)
        .argName("seq limit")
        .desc("The maximum number of seq entries to process. This is useful because running on the entire DB can " +
            "require a lot of time and memory.")
        .hasArg()
        .longOpt("seq-limit")
        .type(Integer.class)
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
      HELP_FORMATTER.printHelp(SarGenerationDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(SarGenerationDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Handle arguments
    MongoDB mongoDB = new MongoDB("localhost", 27017, cl.getOptionValue(OPTION_DB));

    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    outputFile.createNewFile();

    Integer limit = Integer.MAX_VALUE;
    if (cl.hasOption(OPTION_LIMIT)) {
      limit = Integer.parseInt(cl.getOptionValue(OPTION_LIMIT));
    }

    Double threshold = 0D;
    if (cl.hasOption(OPTION_CARBON_THRESHOLD)) {
      threshold = Double.parseDouble(cl.getOptionValue(OPTION_CARBON_THRESHOLD));
    }

    LOGGER.info("Parsed arguments and started up mongo db.");

    McsCalculator calculator = new McsCalculator();
    EnzymeGroupCharacterizer enzymeGroupCharacterizer =
        new OneSubstrateMcsCharacterizer(mongoDB, calculator, threshold);
    StrictSeqGrouper enzymeGrouper = new StrictSeqGrouper(mongoDB.getSeqIterator(), limit);

    SarCorpus corpus = new SarCorpus(enzymeGrouper.getSeqGroups(), enzymeGroupCharacterizer);
    corpus.buildSarCorpus();
    LOGGER.info("Built sar corpus. Printing to file in json format.");

    corpus.printToJsonFile(outputFile);
    LOGGER.info("Complete!");
  }
}
