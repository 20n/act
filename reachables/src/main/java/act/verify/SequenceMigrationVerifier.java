package act.verify;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.mongodb.BasicDBObject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class verifies that sequences previously attached to BRENDA reactions' protein objects now live in the `seq`
 * collection.  It accomplishes this by iterating over the old DB's BRENDA reactions, finding the associated sequence
 * ids, looking for those in the new DB's `seq` collection, following the reaction links from those `seq` entries to
 * the corresponding reactions, and matching the new DB's reaction's substrates/products/name/ec. number
 * against the old.
 */
public class SequenceMigrationVerifier {
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write a proper help message"
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder()
        .argName("host")
        .desc("A the host of the pre-migration mongo db ")
        .hasArg().required()
        .longOpt("old-db-host")
    );
    add(Option.builder()
        .argName("port")
        .desc("A the port of the pre-migration mongo db ")
        .hasArg().required()
        .longOpt("old-db-port")
    );

    add(Option.builder()
        .argName("host")
        .desc("A the host of the post-migration mongo db ")
        .hasArg().required()
        .longOpt("new-db-host")
    );
    add(Option.builder()
        .argName("port")
        .desc("A the port of the post-migration mongo db ")
        .hasArg().required()
        .longOpt("new-db-port")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  public static void main(String args[]) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // TODO: constants?
    MongoDB oldDB =
        new MongoDB(cl.getOptionValue("old-db-host"), Integer.parseInt(cl.getOptionValue("old-db-port")), "actv01");
    MongoDB newDB =
        new MongoDB(cl.getOptionValue("new-db-host"), Integer.parseInt(cl.getOptionValue("new-db-port")), "actv01");

    int failureCount = 0;
    int successCount = 0;
    int multipleMatchesCount = 0;

    // Create an iterator over BRENDA reactions in the old DB with no timeout and no field restrictions.
    DBIterator reactionIterator = oldDB.getIteratorOverReactions(new BasicDBObject("datasource", "BRENDA"), true, null);
    // getNextReaction calls getNext for us and returns null when it's done.
    Reaction oldReaction;
    while((oldReaction = oldDB.getNextReaction(reactionIterator)) != null) {
      String ecNumber = oldReaction.getECNum();

      Set<String> oldRxnSubstrates = chemIdsToInChIs(oldDB, Arrays.asList(oldReaction.getSubstrates()));
      Set<String> oldRxnProducts = chemIdsToInChIs(oldDB, Arrays.asList(oldReaction.getProducts()));

      Set<JSONObject> proteinData =  oldReaction.getProteinData();
      for (JSONObject protein : proteinData) {
        JSONArray sequences = protein.getJSONArray("sequences");
        if (sequences == null) {
          continue;
        }
        for (int i = 0; i < sequences.length(); i++) {
          boolean failure = false;

          JSONObject sequence = sequences.getJSONObject(i);
          int brendaId = sequence.getInt("seq_brenda_id");
          String brendaName = sequence.getString("seq_name");
          String seqText = sequence.getString("seq_sequence");
          String accessionCode = sequence.getString("sec_acc");

          // Construct a query to find this sequence in the new DB.
          BasicDBObject seqQuery = new BasicDBObject().
              append("src", "brenda").
              append("ecnum", ecNumber).
              append("metadata.name", brendaName).
              append("metadata.comment.text", brendaId).
              append("metadata.accession", accessionCode);

          boolean foundMatch = false;
          DBIterator newSeqIterator = newDB.getIteratorOverSeq(seqQuery, true, null);
          Seq newSeq;
          while((newSeq = newDB.getNextSeq(newSeqIterator)) != null) {
            if (foundMatch) {
              // TODO: use a logger instead.
              System.err.format("WARNING: found multiple matches for sequence %s\n", brendaName);
              multipleMatchesCount++;
            }
            foundMatch = true;

            if (!seqText.equals(newSeq.get_sequence())) {
              System.err.format("Sequence mismatch for %s\n", brendaName);
              failure = true;
              continue;
            }

            // Only use the diverse s/p for now, which contain cofactors.  The uniform ones aren't populated.
            Set<String> seqSubstrates = chemIdsToInChIs(newDB, newSeq.getCatalysisSubstratesDiverse());
            Set<String> seqProducts = chemIdsToInChIs(newDB, newSeq.getCatalysisProductsDiverse());

            if (!seqSubstrates.equals(oldRxnSubstrates)) {
              System.err.format("Substrates mismatch for %s\n", brendaName);
              failure = true;
              continue;
            }

            if (!seqProducts.equals(oldRxnProducts)) {
              System.err.format("Products mismatch for %s\n", brendaName);
              failure = true;
              continue;
            }

            // TODO: fetch the corresponding reaction and perform the same test.
          }

          if (!foundMatch) {
            failure = true;
          }

          if (failure) {
            failureCount++;
          } else {
            successCount++;
          }
        }
      }
    }

    System.out.format("Successes: %08d\n", successCount);
    System.out.format("Failures:  %08d\n", failureCount);
    System.out.format("Multiple Matches: %08d\n", multipleMatchesCount);
  }

  private static Set<String> chemIdsToInChIs(MongoDB db, Collection<Long> chemIds) {
    // TODO: should we preserve order?  Not for this purpose, methinks, as s/p sets aren't necessarily ordered.
    Set<String> inchis = new HashSet<>(chemIds.size());
    for (Long id : chemIds) {
      Chemical c = db.getChemicalFromChemicalUUID(id);
      inchis.add(c.getInChI());
    }

    return inchis;
  }
}
