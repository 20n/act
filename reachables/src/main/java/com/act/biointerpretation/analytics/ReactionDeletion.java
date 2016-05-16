package com.act.biointerpretation.analytics;

import act.server.DBIterator;
import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.utils.TSVWriter;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactionDeletion {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionDeletion.class);
  public static final String OPTION_OUTPUT_PATH = "o";
  public static final String OPTION_SOURCE_DB = "r";
  public static final String OPTION_SINK_DB = "k";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is used to find all reactions that were not carried forward from a read to a write DB. ",
      "This analysis only applies to a pair of consecutive DBs in the biointepretation pipeline, and does not span ",
      "multiple processing steps."
  } , "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output path")
        .desc("A path to where output should be written")
        .hasArg().required()
        .longOpt("output")
    );
    add(Option.builder(OPTION_SOURCE_DB)
        .argName("db name")
        .desc("DB from which reactions were read")
        .hasArg().required()
        .longOpt("source")
    );
    add(Option.builder(OPTION_SINK_DB)
        .argName("db name")
        .desc("DB to which reactions were written")
        .hasArg().required()
        .longOpt("sink")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

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

    if (!cl.hasOption(OPTION_OUTPUT_PATH)) {
      LOGGER.error("Input -o prefix");
      return;
    }

    NoSQLAPI srcApi = new NoSQLAPI(cl.getOptionValue(OPTION_SOURCE_DB), cl.getOptionValue(OPTION_SOURCE_DB));
    NoSQLAPI sinkApi = new NoSQLAPI(cl.getOptionValue(OPTION_SINK_DB), cl.getOptionValue(OPTION_SINK_DB));

    searchForDroppedReactions(srcApi, sinkApi, new File(cl.getOptionValue(OPTION_OUTPUT_PATH)));
  }

  private static final List<String> OUTPUT_HEADER = Arrays.asList(
      "id",
      "susbrates",
      "products",
      "ecnum",
      "easy_desc"
  );

  public static void searchForDroppedReactions(NoSQLAPI srcApi, NoSQLAPI sinkApi, File outputFile) throws IOException {
    Set<Long> srcIds = new HashSet<>();
    DBIterator iterator = srcApi.getReadDB().getIteratorOverReactions(
        new BasicDBObject("$query", new BasicDBObject()).append("$orderby", new BasicDBObject("_id", 1)),
        true,
        new BasicDBObject("_id", true));

    while (iterator.hasNext()) {
      DBObject obj = iterator.next();
      Object id = obj.get("_id");
      if (id instanceof Long) {
        srcIds.add((Long) id);
      } else {
        String msg = String.format("Found non-long value for _id in src DB: %s", id);
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
    }
    iterator.close();

    int originalSrcIdSize = srcIds.size();

    Iterator<Reaction> sinkRxns = sinkApi.readRxnsFromInKnowledgeGraph();
    while (sinkRxns.hasNext()) {
      Reaction rxn = sinkRxns.next();
      for (JSONObject protein : rxn.getProteinData()) {
        if (protein.has("source_reaction_id")) {
          Long srcId = protein.getLong("source_reaction_id");
          srcIds.remove(srcId);
        } else
          LOGGER.error("Found protein without source id for reaction %d", rxn.getUUID());
      }
    }

    LOGGER.info("Accounted for %d of %d source reactions in provenance chain", srcIds.size(), originalSrcIdSize);

    List<Long> sortedSrcIds = new ArrayList<>(srcIds);
    Collections.sort(sortedSrcIds);

    try (TSVWriter<String, String> writer = new TSVWriter<>(OUTPUT_HEADER)) {
      writer.open(outputFile);

      for (Long id : sortedSrcIds) {
        Reaction rxn = srcApi.readReactionFromInKnowledgeGraph(id);
        if (rxn == null) {
          LOGGER.error("Could not read reaction %d from source DB", id);
          continue;
        }
        Map<String, String> row = new HashMap<String, String>(OUTPUT_HEADER.size()) {{
          put("id", Long.valueOf(rxn.getUUID()).toString());
          put("substrates", StringUtils.join(rxn.getSubstrates(), ","));
          put("products", StringUtils.join(rxn.getProducts(), ","));
          put("ecnum", rxn.getECNum());
          put("easy_desc", rxn.getReactionName());
        }};
        writer.append(row);
        writer.flush();
      }
    }
  }
}
