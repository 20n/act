package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Seq;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SarGenerationDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarGenerationDriver.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_CARBON_THRESHOLD = "t";
  private static final String OPTION_LIMIT = "l";
  private static final String OPTION_HELP = "h";
  private static final String OPTION_REACTIONS = "r";
  private static final String OPTION_REACTION_LIST = "r";
  private static final String OPTION_REACTIONS_FILE = "f";

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
    add(Option.builder(OPTION_REACTION_LIST)
        .argName("specific reactions")
        .desc("A list of reaction IDs to build a SAR from.")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("specific reactions")
    );
    add(Option.builder(OPTION_REACTIONS_FILE)
        .argName("reactions file")
        .desc("Absolute path to file with one reaction group per line. Each line should be comma separate values, " +
            "with the first value being the name of the group, and the subsequent values being reaction ids.")
        .hasArg()
        .longOpt("reactions-file")
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

    Iterable<ReactionGroup> groups = null;

    if (cl.hasOption(OPTION_REACTION_LIST)) {
      LOGGER.info("Using specific input reactions.");
      ReactionGroup group = new ReactionGroup("ONLY_GROUP");
      for (String idString : cl.getOptionValues(OPTION_REACTION_LIST)) {
        group.addReactionId(Long.parseLong(idString));
      }
      groups = Arrays.asList(group);
    }
    if (cl.hasOption(OPTION_REACTIONS_FILE)) {
      LOGGER.info("Using reactions file.");
      File txtFile = new File(cl.getOptionValue(OPTION_REACTIONS_FILE));
      ReactionGroupCorpus corpus = ReactionGroupCorpus.loadFromTextFile(txtFile);
      groups = corpus;
    }

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
    FullReactionBuilder reactionBuilder = new FullReactionBuilder();
    ErosCorpus roCorpus = new ErosCorpus();
    roCorpus.loadValidationCorpus();
    EnzymeGroupCharacterizer enzymeGroupCharacterizer =
        new OneSubstrateMcsCharacterizer(mongoDB, calculator, reactionBuilder, roCorpus, threshold);

    if (groups == null) {
      LOGGER.info("Scanning seq db for reactions with same seq.");
      SeqDBReactionGrouper enzymeGrouper = new SeqDBReactionGrouper(mongoDB.getSeqIterator(), limit);
      groups = enzymeGrouper.getReactionGroupCorpus();
    }

    SarCorpusBuilder corpusBuilder =new SarCorpusBuilder(groups, enzymeGroupCharacterizer);
    SarCorpus sarCorpus = corpusBuilder.build();
    LOGGER.info("Built sar corpus. Printing to file in json format.");

    sarCorpus.printToJsonFile(outputFile);
    LOGGER.info("Complete!");
  }
}
