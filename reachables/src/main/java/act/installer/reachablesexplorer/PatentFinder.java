package act.installer.reachablesexplorer;

import act.installer.pubchem.PubchemSynonymType;
import com.act.utils.CLIUtil;
import com.twentyn.patentSearch.Searcher;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatentFinder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PatentFinder.class);

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_TARGET_DB = "t";
  private static final String OPTION_TARGET_REACHABLES_COLLECTION = "c";
  private static final String OPTION_PATENT_INDEX_DIR = "i";

  // Default host. If running on a laptop, please set a SSH bridge to access speakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;

  // Target database and collection. We populate these with reachables
  // TODO These should all be turned into more long-term collections
  private static final String DEFAULT_TARGET_DATABASE = "wiki_reachables";
  private static final String DEFAULT_TARGET_COLLECTION = "reachablesv6_test_thomas";

  private static final String UNUSED_SEQUENCES_COLLECTION = "sequencesv0"; // We won't touch these but need them for
  private static final String UNUSED_ASSETS_DIR = "/tmp";                  // the Loader's constructor.

  // A directory of directories.  Each directory is one year's index, and ends in `.index`.
  private static final String DEFAULT_PATENT_INDEX_LOCATION = "/mnt/shared-data/Mark/patents";


  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class searches for patents related to molecules in a reachables DB, and updates the reachable documents ",
      "with references to those patents.  Patents are filtered by a manually selected relevance threshold."
  }, " ");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB_HOST)
        .argName("DB host")
        .desc(String.format("The database host to which to connect (default: %s)", DEFAULT_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("DB port")
        .desc(String.format("The port on which to connect to the database (default: %d)", DEFAULT_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_PATENT_INDEX_DIR)
        .argName("path")
        .desc(String.format(
            "A path to a directory of per-year indexes (directories) of patents ending in `.index` (default: %s)",
            DEFAULT_PATENT_INDEX_LOCATION))
        .hasArg()
        .longOpt("indexes-dir")
    );
    add(Option.builder(OPTION_TARGET_DB)
        .argName("DB name")
        .desc(String.format("The name of the DB into which to write reachable molecule documents (default: %s)",
            DEFAULT_TARGET_DATABASE))
        .hasArg()
        .longOpt("dest-db-name")
    );
    add(Option.builder(OPTION_TARGET_REACHABLES_COLLECTION)
        .argName("collection name")
        .desc(String.format(
            "The name of the collection in the dest DB to which to add patent references (default: %s)",
            DEFAULT_TARGET_COLLECTION))
        .hasArg()
        .longOpt("reachables-collection")
    );
  }};

  private static final List<PubchemSynonymType> SYNONYM_TYPE_PREFERENCE = Collections.unmodifiableList(Arrays.asList(
      PubchemSynonymType.TRIVIAL_NAME,
      PubchemSynonymType.INTL_NONPROPRIETARY_NAME,
      PubchemSynonymType.DEPOSITORY_NAME, // Beware: this list can be huge.  TODO: be clever and shorten it?
      PubchemSynonymType.DRUG_TRADE_NAME,
      PubchemSynonymType.IUPAC_NAME
  ));

  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(Loader.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    String host = cl.getOptionValue(OPTION_DB_HOST, DEFAULT_HOST);
    Integer port = Integer.parseInt(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_PORT.toString()));
    String dbName = cl.getOptionValue(OPTION_TARGET_DB, DEFAULT_TARGET_DATABASE);
    String collection = cl.getOptionValue(OPTION_TARGET_REACHABLES_COLLECTION, DEFAULT_TARGET_COLLECTION);
    LOGGER.info("Connecting to %s:%d/%s, using collection %s", host, port, dbName, collection);

    Loader loader = new Loader(host, port, dbName, collection, UNUSED_SEQUENCES_COLLECTION, UNUSED_ASSETS_DIR);

    File indexesTopDir = new File(cl.getOptionValue(OPTION_PATENT_INDEX_DIR, DEFAULT_PATENT_INDEX_LOCATION));
    if (!indexesTopDir.exists() || !indexesTopDir.isDirectory()) {
      cliUtil.failWithMessage("Index top-level directory at %s is not a directory", indexesTopDir.getAbsolutePath());
    }

    LOGGER.info("Using index top level dir: %s", indexesTopDir.getAbsolutePath());

    PatentFinder finder = new PatentFinder();
    try (Searcher searcher = Searcher.Factory.getInstance().build(indexesTopDir)) {
      finder.run(loader, searcher);
    }
  }

  private void run(Loader loader, Searcher searcher) throws IOException {
    DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find();

    while (reachableDBCursor.hasNext()) {
      Reachable reachable = reachableDBCursor.next();

      SynonymData synonyms = reachable.getSynonyms();
      Set<String> preferredSynonyms = null;
      if (synonyms != null) {
        Map<PubchemSynonymType, Set<String>> pubchemSynonyms = synonyms.getPubchemSynonyms();
        /* Search for different kinds of synonyms in order of preference (where preference tries to strike a balance
         * between verbosity and specificity).  Stop when we've found a type of synonym that is available for this
         * molecule, and use that in the patent search. */
        for (PubchemSynonymType type : SYNONYM_TYPE_PREFERENCE) {
          if (pubchemSynonyms.containsKey(type)) {
            preferredSynonyms = pubchemSynonyms.get(type);
            break;
          }
        }
      }

      if (preferredSynonyms == null) {
        LOGGER.warn("No synonyms for molecule %s", reachable.getInchi());
        preferredSynonyms = Collections.emptySet();
      }

      List<String> allNames = new ArrayList<>(reachable.getNames());
      allNames.addAll(preferredSynonyms);

      allNames.removeIf(s -> s == null || s.length() < 3); // Eliminate potential garbage rankings for short names.
      // Note: stop words should not appear in the index, so no need to filter on terms.
      Collections.sort(allNames);

      LOGGER.info("Running query with terms: %s", StringUtils.join(allNames, ", "));

      List<Searcher.SearchResult> results = searcher.searchInClaims(allNames);

      if (results.size() > 0) {
        LOGGER.info("Results (%d) for %s:", results.size(), reachable.getPageName());
        List<PatentSummary> summaries = new ArrayList<>(results.size());
        for (Searcher.SearchResult result : results) {
          LOGGER.info("(%.3f) %s: %s", result.getRelevanceScore(), result.getId(), result.getTitle());
          summaries.add(new PatentSummary(result.getId(), result.getTitle(), result.getRelevanceScore()));
        }

        reachable.setPatentSummaries(summaries);
        loader.upsert(reachable);

      } else {
        LOGGER.info("No results for %s", reachable.getPageName());
      }

    }
  }
}
