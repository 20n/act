package com.act.biointerpretation.reactionmerging.analytics;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactionCountProvenance {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionCountProvenance.class);
  private static final String REACTION_ID = "reaction_id";
  private static final String COLLAPSE_COUNT = "collapse_count";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_ORDERED_LIST_OF_DBS = "l";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is used to count the total number of collapsed reactions which happen through the reaction merger class.",
      "It also can take in an input file containing the previous collapsed reactions from the former read DB and updates ",
      "the counts based on that."}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg()
        .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_ORDERED_LIST_OF_DBS)
        .argName("ordered list of DBs")
        .desc("A comma-separated ordered list of DBs in the bio-interpretatiosn pipeline")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("ordered-db-list")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  private Map<Integer, Integer> inputReactionIdToCount;
  private Map<Integer, Integer> outputReactionIdToCount;
  private String outputFileName;
  private List<String> dbs;
  private String firstDb;

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error(String.format("Argument parsing failed: %s\n", e.getMessage()));
      HELP_FORMATTER.printHelp(ReactionCountProvenance.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionCountProvenance.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (!cl.hasOption(OPTION_OUTPUT_PREFIX)) {
      LOGGER.error("Input -o prefix");
      return;
    }

    List<String> dbs = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_ORDERED_LIST_OF_DBS)));
    ReactionCountProvenance reactionCountProvenance = new ReactionCountProvenance(dbs, cl.getOptionValue(OPTION_OUTPUT_PREFIX));
    reactionCountProvenance.run();
    reactionCountProvenance.writeToDisk();
  }

  public ReactionCountProvenance(List<String> dbs, String outputFileName) {
    this.inputReactionIdToCount = new HashMap<>();
    this.outputReactionIdToCount = new HashMap<>();
    this.outputFileName = outputFileName;
    this.dbs = dbs;
    this.firstDb = dbs.get(0);
  }

  private void countProvenance(NoSQLAPI noSQLAPI) {
    LOGGER.info("Starting count provenance on read db %s and write db %s", noSQLAPI.getReadDB().dbs(), noSQLAPI.getWriteDB().dbs());
    Iterator<Reaction> reactionIterator = noSQLAPI.readRxnsFromInKnowledgeGraph();
    while (reactionIterator.hasNext()) {
      Reaction rxn = reactionIterator.next();
      Set<Integer> sourceIds = new HashSet<>();

      Integer collapseCount = outputReactionIdToCount.get(rxn.getUUID()) == null ? 0 : outputReactionIdToCount.get(rxn.getUUID());
      for (JSONObject protein : rxn.getProteinData()) {
        if (protein.has("source_reaction_id")) {
          Integer sourceId = protein.getInt("source_reaction_id");

          // If multiple protein objects were moved from the same reaction, then only process the first one and ignore the rest.
          if (sourceIds.contains(sourceId)) {
            continue;
          }

          sourceIds.add(sourceId);

          Integer scoreToIncrement = 1;
          if (inputReactionIdToCount.containsKey(sourceId)) {
            scoreToIncrement = inputReactionIdToCount.get(sourceId);
          }

          collapseCount += scoreToIncrement;
        } else {
          // We did the write DB here (doesnt matter read or write since we constructed it with the same db) since
          // lucille gets converted to actv01 for the read DB.
          if (!noSQLAPI.getWriteDB().dbs().equals(this.firstDb)) {
            LOGGER.error(String.format("Could not find source_reaction_id in protein of reaction id %d", rxn.getUUID()));
          }
        }
      }
      outputReactionIdToCount.put(rxn.getUUID(), collapseCount);
    }
    LOGGER.info("Finished count provenance on %s", noSQLAPI.getReadDB().dbs());
  }

  private void run() {
    for (String dbName : dbs) {
      // In the first iteration, both fields are empty. In the subsequent iterations, the output of the previous result
      // becomes the new input while the output is re-initialized to an empty map.
      this.inputReactionIdToCount = this.outputReactionIdToCount;
      this.outputReactionIdToCount = new HashMap<>();
      NoSQLAPI noSQLAPI = new NoSQLAPI(dbName, dbName);
      this.countProvenance(noSQLAPI);
    }
  }

  private void writeToDisk() throws IOException {
    List<String> header = new ArrayList<>();
    header.add(REACTION_ID);
    header.add(COLLAPSE_COUNT);

    try (TSVWriter<String, String> writer = new TSVWriter<>(header)) {
      writer.open(new File(this.outputFileName));
      for (Map.Entry<Integer, Integer> entry : outputReactionIdToCount.entrySet()) {
        Map<String, String> row = new HashMap<>();
        row.put(REACTION_ID, entry.getKey().toString());
        row.put(COLLAPSE_COUNT, entry.getValue().toString());
        writer.append(row);
        writer.flush();
      }
    }
  }
}
