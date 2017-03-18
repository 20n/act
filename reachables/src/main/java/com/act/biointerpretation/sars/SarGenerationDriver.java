/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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

  private static final String LOCAL_HOST = "localhost";
  private static final Integer MONGO_PORT = 27017;

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

    // Create DB and DbAPI
    MongoDB mongoDB = new MongoDB(LOCAL_HOST, MONGO_PORT, cl.getOptionValue(OPTION_DB));
    DbAPI dbApi = new DbAPI(mongoDB);

    // Handle output file
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    if (outputFile.isDirectory() || outputFile.exists()) {
      LOGGER.error("Supplied output file is a directory or already exists.");
      HELP_FORMATTER.printHelp(SarGenerationDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }
    outputFile.createNewFile();

    // Check that there is exactly one reaction group input option
    if (cl.hasOption(OPTION_REACTION_LIST) && cl.hasOption(OPTION_REACTIONS_FILE)) {
      LOGGER.error("Cannot process both a reaction list and a reactions file as input.");
      HELP_FORMATTER.printHelp(SarGenerationDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }
    if (!cl.hasOption(OPTION_REACTION_LIST) && !cl.hasOption(OPTION_REACTIONS_FILE)) {
      LOGGER.error("Must supply either a reaction list or a reactions file as input.");
      HELP_FORMATTER.printHelp(SarGenerationDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Build input reaction group corpus.
    Iterable<ReactionGroup> groups = null;
    if (cl.hasOption(OPTION_REACTION_LIST)) {
      LOGGER.info("Using specific input reactions.");
      ReactionGroup group = new ReactionGroup("ONLY_GROUP", "NO_DB");
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
          throw f;
        }
      }
    }

    // Build all pieces of SAR generator
    ReactionProjector projector = new ReactionProjector();
    ExpandedReactionSearcher generalizer = new ExpandedReactionSearcher(projector);

    McsCalculator reactionMcsCalculator = new McsCalculator(McsCalculator.REACTION_BUILDING_OPTIONS);
    McsCalculator sarMcsCalculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    FullReactionBuilder reactionBuilder = new FullReactionBuilder(reactionMcsCalculator, generalizer, projector);

    SarFactory substructureSarFactory = new OneSubstrateSubstructureSar.Factory(sarMcsCalculator);
    SarFactory carbonCountSarFactory = new OneSubstrateCarbonCountSar.Factory();
    List<SarFactory> sarFactories = Arrays.asList(carbonCountSarFactory, substructureSarFactory);

    ErosCorpus roCorpus = new ErosCorpus();
    roCorpus.loadValidationCorpus();

    ReactionGroupCharacterizer reactionGroupCharacterizer =
        new OneSubstrateOneRoCharacterizer(dbApi, sarFactories, reactionBuilder, roCorpus);
    SarCorpusBuilder corpusBuilder = new SarCorpusBuilder(groups, reactionGroupCharacterizer);
    LOGGER.info("Parsed arguments and constructed SAR corpus builder. Building corpus.");

    SarCorpus sarCorpus = corpusBuilder.build();
    LOGGER.info("Built sar corpus. Printing to file in json format.");

    sarCorpus.printToJsonFile(outputFile);
    LOGGER.info("Complete!");
  }
}
