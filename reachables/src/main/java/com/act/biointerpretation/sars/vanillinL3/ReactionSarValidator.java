package com.act.biointerpretation.sars.vanillinL3;

import act.server.MongoDB;
import act.shared.Reaction;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SeqDBReactionGrouper;
import com.act.jobs.FileChecker;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionSarValidator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionSarValidator.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_INPUT_PATH = "i";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class takes in a list of reaction IDs, calculates SARs for the associated sequences, and spits out any " +
          "SARs that it finds to support each reaction.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .required(true)
    );
    add(Option.builder(OPTION_INPUT_PATH)
        .argName("input path")
        .desc("The file path to the input reactions, with one reaction ID per line.")
        .longOpt("input-path")
        .required(true)
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output file path")
        .desc("The absolute path to the file to which to write the json file of the reaction group corpus.")
        .hasArg()
        .longOpt("output-file-path")
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
      HELP_FORMATTER.printHelp(SeqDBReactionGrouper.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(SeqDBReactionGrouper.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    MongoDB db = new MongoDB(LOCAL_HOST, MONGO_PORT, cl.getOptionValue(OPTION_DB));

    File inputFile = new File(cl.getOptionValue(OPTION_INPUT_PATH));
    FileChecker.verifyInputFile(inputFile);
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    FileChecker.verifyAndCreateOutputFile(outputFile);

    List<Long> rxnIds = getReactions(inputFile);

    Map<Long, List<Sar>> sarMap = new HashMap<>();

    for (Long rxnId : rxnIds) {
      sarMap.put(rxnId, new ArrayList<>());

      Reaction reaction = db.getReactionFromUUID(rxnId);

      for (Long seq : reaction.getSeqs()) {
        for (Integer ro : reaction.getRos()) {
          List<Sar> sars = getSars(seq, ro);
          sars.stream().filter(sar -> sar.test(getSubstrateMolecules(reaction)));
          sarMap.get(rxnId).addAll(sars);
        }
      }
    }

    writeSarMapToFile(sarMap, outputFile);
  }

  public static List<Long> getReactions(File inputFile) throws IOException {
    List<Long> result = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        result.add(Long.parseLong(line));
      }
    }
    return result;
  }

  /**
   * Writes the SAR map to file!
   */
  public static void writeSarMapToFile(Map<Long, List<Sar>> sarMap, File outputFile) {
    throw new NotImplementedException();
  }

  /**
   * Computes the SARs matching this seq and RO.
   * @param seq The seq id.
   * @param ro The RO to use.
   */
  public static List<Sar> getSars(Long seq, Integer ro) {
    throw new NotImplementedException("Not yet implemented!");
  }

  /**
   * Computes the substrate molecules for the reaction.
   */
  public static List<Molecule> getSubstrateMolecules(Reaction reaction) {
    throw new com.jacob.com.NotImplementedException("Not yet implemented!");
  }
}
