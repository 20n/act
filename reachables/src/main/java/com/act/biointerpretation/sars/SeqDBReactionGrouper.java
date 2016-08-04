package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Seq;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A sequence grouper that iterates over the seq DB and groups only seq entries that have exactly same sequence.
 */
public class SeqDBReactionGrouper {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SeqDBReactionGrouper.class);

  private static final String OPTION_DB = "db";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_LIMIT = "l";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class is used to generate reaction groups by scanning the seq DB for sequences that point to multiple " +
          "reactions.  Options are supplied to indicate how far into the DB to scan, which DB to use, and where to " +
          "write the output.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .required(true)
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output file path")
        .desc("The absolute path to the file to which to write the json file of the reaction group corpus.")
        .hasArg()
        .longOpt("output-file-path")
        .required(true)
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

  private static final String LOCAL_HOST = "localhost";
  private static final Integer MONGO_PORT = 27017;
  private static final Integer DEFAULT_LIMIT_INFINITY = Integer.MAX_VALUE;

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

    // Handle arguments
    String mongoDBName = cl.getOptionValue(OPTION_DB);
    MongoDB mongoDB = new MongoDB(LOCAL_HOST, MONGO_PORT, mongoDBName);

    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    if (outputFile.isDirectory() || outputFile.exists()) {
      LOGGER.error("Supplied output file is a directory or already exists.");
      System.exit(1);
    }
    outputFile.createNewFile();

    Integer limit = DEFAULT_LIMIT_INFINITY;
    if (cl.hasOption(OPTION_LIMIT)) {
      limit = Integer.parseInt(cl.getOptionValue(OPTION_LIMIT));
    }
    LOGGER.info("Only processing first %d entries in Seq DB.", limit);

    SeqDBReactionGrouper enzymeGrouper = new SeqDBReactionGrouper(mongoDB.getSeqIterator(), mongoDBName, limit);

    LOGGER.info("Scanning seq db for reactions with same seq.");
    ReactionGroupCorpus groupCorpus = enzymeGrouper.getReactionGroupCorpus();

    LOGGER.info("Writing output to file.");
    groupCorpus.printToJsonFile(outputFile);

    LOGGER.info("Complete!");
  }


  final Integer limit;
  final String dbName;
  final Iterator<Seq> seqIterator;

  /**
   * Builds a SeqDBReactionGrouper for the given Seq entries.
   *
   * @param seqIterator The Seq entries to group.
   * @param limit The maximum number of entries to process. This can be used to limit memory and time.
   */
  public SeqDBReactionGrouper(Iterator<Seq> seqIterator, String dbName, Integer limit) {
    this.seqIterator = seqIterator;
    this.dbName = dbName;
    this.limit = limit;
  }

  /**
   * Builds a SeqDBReactionGrouper for the given Seq entries.
   *
   * @param seqIterator The Seq entries to group.
   */
  public SeqDBReactionGrouper(Iterator<Seq> seqIterator, String dbName) {

    this(seqIterator, dbName, DEFAULT_LIMIT_INFINITY);
  }

  /**
   * Returns the collection of SeqGroups produced by running this grouper on the Seq entries from the DB.
   * TODO: Implement this in a way that doesn't store the whole map in memory at the same time.
   *
   * @return The collection of produced SeqGroups.
   */

  public ReactionGroupCorpus getReactionGroupCorpus() {
    Map<String, ReactionGroup> sequenceToReactionGroupMap = getSequenceToReactionGroupMap(seqIterator);
    LOGGER.info("Done getting seq group map, found %d distinct SeqGroups.", sequenceToReactionGroupMap.size());
    return new ReactionGroupCorpus(sequenceToReactionGroupMap.values());
  }

  /**
   * Iterates over seq entries and builds a map from unique sequences to ReactionGroup objects that list their
   * corresponding Seq entry ids and Reaction ids.
   *
   * @param seqIterator
   * @return
   */
  private Map<String, ReactionGroup> getSequenceToReactionGroupMap(Iterator<Seq> seqIterator) {
    Map<String, ReactionGroup> sequenceToReactionGroupMap = new HashMap<>();

    Integer counter = 0;
    while (seqIterator.hasNext()) {
      if (counter >= limit) {
        break;
      }
      if (counter % 1000 == 0) {
        LOGGER.info("Processed %d seq entries so far", counter);
      }

      Seq seq = seqIterator.next();
      String sequence = seq.get_sequence();

      ReactionGroup group = sequenceToReactionGroupMap.get(sequence);

      if (group == null) {
        group = new ReactionGroup("SEQ_ID_" + Integer.toString(seq.getUUID()), dbName);
        sequenceToReactionGroupMap.put(sequence, group);
      }

      for (Long reactionId : seq.getReactionsCatalyzed()) {
        group.addReactionId(reactionId);
      }
      counter++;
    }

    return sequenceToReactionGroupMap;
  }
}
