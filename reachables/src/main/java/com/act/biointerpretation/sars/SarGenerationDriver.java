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

import java.util.ArrayList;
import java.util.List;

public class SarGenerationDriver {

  private static final String DB = "marvin";
  private static final Logger LOGGER = LogManager.getFormatterLogger(SarGenerationDriver.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class is used to generate SARs from an instance of the MongoDB.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .type(String.class)
        .required(true)
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

  public static void main(String[] args) {
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

    MongoDB mongoDB = new MongoDB("localhost", 27017, cl.getOptionValue(OPTION_DB));
    LOGGER.info("Parsed arguments and started up mongo db.");

    SarGenerator sarGenerator = new MCSGenerator();
    Iterable<SeqGroup> enzymeGroups = new StrictSeqGrouper(mongoDB.getSeqIterator(), 300);

    SarCorpus corpus = new SarCorpus(enzymeGroups, sarGenerator);
    corpus.buildSarCorpus();
    LOGGER.info("Built sar corpus.");

    for (CharacterizedGroup group : corpus) {
      LOGGER.info(group);
    }
    LOGGER.info("Complete!");
  }
}
