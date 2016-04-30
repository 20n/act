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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactionCountProvenance {
  private static final Logger LOGGER = LogManager.getLogger(ReactionCountProvenance.class);
  private static final String REACTION_ID = "reaction_id";
  private static final String COLLAPSE_COUNT = "collapse_count";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_INPUT_TSV_COUNT_PROVENANCE_FILE = "i";
  private static final String READ_DB = "actv01";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is used to count the total number of collapsed reactions which happen through the reaction merger class." +
          "It also can take in an input file containing the previous collapsed reactions from the former read DB and updates " +
          "the counts based on that."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg()
        .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_INPUT_TSV_COUNT_PROVENANCE_FILE)
        .argName("input count provenance tsv file")
        .desc("A tsv file containing a mapping of reaction id to a count of reactions that have collapsed to it")
        .longOpt("input-count-provenance")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  private NoSQLAPI api;
  private Map<Integer, Integer> inputReactionIdToCount;
  private Map<Integer, Integer> outputReactionIdToCount;
  private String outputFileName;

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

    Map<Integer, Integer> reactionIdToCollapseCount = new HashMap<>();

    if (cl.hasOption(OPTION_INPUT_TSV_COUNT_PROVENANCE_FILE)) {
      TSVParser parser = new TSVParser();
      parser.parse(new File(cl.getOptionValue(OPTION_INPUT_TSV_COUNT_PROVENANCE_FILE)));
      List<String> headers = parser.getHeader();

      for (String header : headers) {
        if (!header.equals(REACTION_ID) && !header.equals(COLLAPSE_COUNT)) {
          LOGGER.error("Input tsv file does not have the correct column names");
          System.exit(1);
        }
      }

      for (Map<String, String> row : parser.getResults()) {
        reactionIdToCollapseCount.put(Integer.valueOf(row.get(REACTION_ID)), Integer.valueOf(row.get(COLLAPSE_COUNT)));
      }
    }

    ReactionCountProvenance reactionCountProvenance = new ReactionCountProvenance(new NoSQLAPI(READ_DB, "FAKE"),
        reactionIdToCollapseCount, cl.getOptionValue(OPTION_OUTPUT_PREFIX));
    reactionCountProvenance.run();
    reactionCountProvenance.writeToDisk();
  }

  public ReactionCountProvenance(NoSQLAPI api, Map<Integer, Integer> reactionToCount, String outputFileName) {
    this.api = api;
    this.inputReactionIdToCount = reactionToCount;
    this.outputReactionIdToCount = new HashMap<>();
    this.outputFileName = outputFileName;
  }

  private void run() {
    Iterator<Reaction> reactionIterator = api.readRxnsFromInKnowledgeGraph();
    while (reactionIterator.hasNext()) {
      Reaction rxn = reactionIterator.next();
      Set<JSONObject> proteinData = new HashSet<>(rxn.getProteinData());
      Set<Integer> sourceIds = new HashSet<>();

      for (JSONObject protein : proteinData) {
        Integer sourceId = protein.getInt("source_reaction_id");
        if (sourceId == null) {
          LOGGER.error(String.format("Could not find source_reaction_id in protein of reaction id %d", rxn.getUUID()));
          return;
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
