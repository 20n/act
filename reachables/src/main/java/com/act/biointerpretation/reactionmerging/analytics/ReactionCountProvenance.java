package com.act.biointerpretation.reactionmerging.analytics;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.utils.TSVParser;
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
      "This class is used to count the total number of collapsed reactions which happen through the reaction merger class." +
      "It also can take in an input file containing the previous collapsed reactions from the former read DB and updates " +
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
        .hasArg()
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
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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

    List<String> dbs = new ArrayList<>(Arrays.asList(cl.getOptionValue(OPTION_ORDERED_LIST_OF_DBS).split(",")));
    if (dbs.size() < 2) {
      throw new RuntimeException("There has to be at least two DBs (the read db and the write db) for count provenance" +
          "to work");
    }

    ReactionCountProvenance reactionCountProvenance = new ReactionCountProvenance(dbs, cl.getOptionValue(OPTION_OUTPUT_PREFIX));
    reactionCountProvenance.run();
    reactionCountProvenance.writeToDisk();
  }

  public ReactionCountProvenance(List<String> dbs, String outputFileName) {
    this.inputReactionIdToCount = new HashMap<>();
    this.outputReactionIdToCount = new HashMap<>();
    this.outputFileName = outputFileName;
    this.dbs = dbs;
  }

  private void countProvenance(NoSQLAPI noSQLAPI) {
    LOGGER.info("Starting count provenance on %s", noSQLAPI.getReadDB().dbs());
    System.out.println(String.format("Starting count provenance on %s", noSQLAPI.getReadDB().dbs()));
    Iterator<Reaction> reactionIterator = noSQLAPI.readRxnsFromInKnowledgeGraph();
    while (reactionIterator.hasNext()) {
      Reaction rxn = reactionIterator.next();
      Set<JSONObject> proteinData = new HashSet<>(rxn.getProteinData());
      Set<Integer> sourceIds = new HashSet<>();

      for (JSONObject protein : proteinData) {
        if (protein.has("source_reaction_id")) {
          Integer sourceId = protein.getInt("source_reaction_id");
          if (sourceId == null) {
            LOGGER.debug(String.format("Could not find source_reaction_id in protein of reaction id %d", rxn.getUUID()));
            continue;
          }

          // If multiple protein objects were moved from the same reaction, then only process the first one and ignore the rest.
          if (sourceIds.contains(sourceId)) {
            continue;
          }

          sourceIds.add(sourceId);

          Integer scoreToIncrement = inputReactionIdToCount.get(sourceId);
          if (scoreToIncrement == null) {
            scoreToIncrement = 1;
          }

          Integer collapseCount = outputReactionIdToCount.get(rxn.getUUID()) == null ? 0 : outputReactionIdToCount.get(rxn.getUUID());
          collapseCount += scoreToIncrement;
          outputReactionIdToCount.put(rxn.getUUID(), collapseCount);
        }
      }
    }
    System.out.println(String.format("Finished count provenance on %s", noSQLAPI.getReadDB().dbs()));
    LOGGER.info("Finished count provenance on %s", noSQLAPI.getReadDB().dbs());
  }

  private void run() {
    for (String dbName : dbs) {
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

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(this.outputFileName));

    for (Map.Entry<Integer, Integer> entry : outputReactionIdToCount.entrySet()) {
      Map<String, String> row = new HashMap<>();
      row.put(entry.getKey().toString(), entry.getValue().toString());
      writer.append(row);
      writer.flush();
    }

    writer.close();
  }
}
