package com.act.biointerpretation.sars;

import act.server.MongoDB;
import com.act.biointerpretation.Utils.ReactionProjector;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SarGenerationDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarGenerationDriver.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_HELP = "h";
  private static final String OPTION_REACTION_LIST = "r";
  private static final String OPTION_REACTIONS_FILE = "f";

  public static final String HELP_MESSAGE =
      "This class is used to generate SARs from a set of reactions or chemicals.  It has several modes of operation, " +
          "which are described in more detail along with the operations that run them.";

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
    add(Option.builder(OPTION_REACTION_LIST)
        .argName("specific reactions")
        .desc("A list of reaction IDs to build a SAR from.")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("specific reactions")
    );
    add(Option.builder(OPTION_REACTIONS_FILE)
        .argName("reactions file")
        .desc("Absolute path to file from which to read reaction groups.  File should either be a ReactionGroupCorpus " +
            "in json format, or a file with one reaction group per line, where each line has comma separate values, " +
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
    DbAPI dbApi = new DbAPI(mongoDB);

    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    outputFile.createNewFile();

    if (cl.hasOption(OPTION_REACTION_LIST) && cl.hasOption(OPTION_REACTIONS_FILE)) {
      LOGGER.error("Cannot process both a reaction list and a reactions file as input.");
      return;
    }
    if (!cl.hasOption(OPTION_REACTION_LIST) && !cl.hasOption(OPTION_REACTIONS_FILE)) {
      LOGGER.error("Must supply either a reaction list or a reactions file as input.");
      return;
    }

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
      File inputFile = new File(cl.getOptionValue(OPTION_REACTIONS_FILE));
      try {
        groups = ReactionGroupCorpus.loadFromJsonFile(inputFile);
        LOGGER.info("Successfully parsed input as json file.");
      } catch (IOException e) {
        LOGGER.info("Input file not json file. Trying txt format.");
        try {
          groups = ReactionGroupCorpus.loadFromTextFile(inputFile);
          LOGGER.info("Successfully parsed input as text file.");
        } catch (IOException f) {
          LOGGER.error("Reactions input file not parseable. %s", f.getMessage());
          return;
        }
      }
    }

    ReactionProjector projector = new ReactionProjector();
    GeneralReactionSearcher generalizer = new GeneralReactionSearcher(projector);

    McsCalculator reactionMcsCalculator = new McsCalculator(McsCalculator.REACTION_BUILDING_OPTIONS);
    McsCalculator sarMcsCalculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(reactionMcsCalculator, generalizer, projector);

    SarBuilder substructureSarBuilder = new OneSubstrateSubstructureSar.Builder(sarMcsCalculator);
    SarBuilder carbonCountSarBuilder = new OneSubstrateCarbonCountSar.Builder();
    List<SarBuilder> sarBuilders = Arrays.asList(carbonCountSarBuilder, substructureSarBuilder);

    ErosCorpus roCorpus = new ErosCorpus();
    roCorpus.loadValidationCorpus();

    EnzymeGroupCharacterizer enzymeGroupCharacterizer =
        new UniformGroupCharacterizer(dbApi, sarBuilders, reactionBuilder, roCorpus);

    LOGGER.info("Parsed arguments and started up mongo db.");

    SarCorpusBuilder corpusBuilder = new SarCorpusBuilder(groups, enzymeGroupCharacterizer);
    SarCorpus sarCorpus = corpusBuilder.build();
    LOGGER.info("Built sar corpus. Printing to file in json format.");

    sarCorpus.printToJsonFile(outputFile);
    LOGGER.info("Complete!");
  }
}
