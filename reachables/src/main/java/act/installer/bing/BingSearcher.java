package act.installer.bing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.act.utils.CLIUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.mongodb.BasicDBObject;

import act.server.MongoDB;

/**
 * This class contains the main logic for installing Bing Search results in the Installer DB
 */

public class BingSearcher {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearcher.class);
  private static final String USAGE_TERMS_FILENAME = "usage_terms.txt";

  public static final String HELP_MESSAGE =
      "This class contains the main logic for installing Bing Search results in the Installer DB.";

  public static final String OPTION_DB_NAME = "n";
  public static final String OPTION_DB_PORT = "p";
  public static final String OPTION_DB_HOST = "h";
  public static final String OPTION_CACHE_ONLY = "c";

  public static final String DEFAULT_HOST = "localhost";
  public static final String DEFAULT_PORT = "27017";
  public static final String DEFAULT_DATABASE = "actv01";


  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB_NAME)
        .argName("db name")
        .desc(String.format("The name of the database from which to fetch chemicals inchis and " +
            "write Bing cross-references (default: %s). It needs to contains a 'chemicals' collection.",
            DEFAULT_DATABASE))
        .hasArg()
        .longOpt("db")
        .type(String.class)
    );
    add(Option.builder(OPTION_DB_HOST)
        .argName("DB host")
        .desc(String.format("The database host to which to connect (default: %s)", DEFAULT_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("DB port")
        .desc(String.format("The port on which to connect to the database (default: %s)", DEFAULT_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_CACHE_ONLY)
        .argName("CACHE_ONLY")
        .desc("Use the cache only. If that option is used, they will not be any queries against the Bing search API.")
        .longOpt("cache-only")
        .type(String.class)
    );
  }};


  private MongoDB db;
  private BingSearchResults bingSearchResults;
  private Set<String> usageTerms;
  private boolean forceUpdate;
  private boolean cacheOnly;

  public static void main(String args[]) {

    CLIUtil cliUtil = new CLIUtil(BingSearcher.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);
    MongoDB db = new MongoDB(
        cl.getOptionValue(OPTION_DB_HOST, DEFAULT_HOST),
        Integer.parseInt(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_PORT)),
        cl.getOptionValue(OPTION_DB_NAME, DEFAULT_DATABASE)
    );
    BingSearcher bingSearcher = new BingSearcher(db, false, cl.hasOption(OPTION_CACHE_ONLY));
    bingSearcher.addBingSearchResultsForEntireDatabase();
  }

  public BingSearcher(MongoDB db) {
    this(db, false, false);
  }

  public BingSearcher(MongoDB db, boolean forceUpdate, boolean cacheOnly) {
    this.db = db;
    this.forceUpdate = forceUpdate;
    this.cacheOnly = cacheOnly;
    this.bingSearchResults = new BingSearchResults(true);
    // Get the usage terms
    LOGGER.debug("Getting usage terms corpus.");
    UsageTermsCorpus usageTermsCorpus = new UsageTermsCorpus(USAGE_TERMS_FILENAME);
    try {
      usageTermsCorpus.buildCorpus();
    } catch (IOException e) {
      LOGGER.error("Usage term corpus source file not found in class resources: %s", USAGE_TERMS_FILENAME);
      System.exit(1);
    }
    this.usageTerms = usageTermsCorpus.getUsageTerms();
  }

  private void addBingSearchResultsForEntireDatabase() {
    Map<String, Long> m = db.constructAllInChIs();
    Set<String> inchis = m.keySet();
    addBingSearchResultsForInchiSet(inchis);
  }

  private void addBingSearchResultsForInChI(String inchi) throws IOException {
    LOGGER.debug("Processing InChI " + inchi);
    // Fetches the names (Brenda, Metacyc, Chebi, Drugbank)
    NamesOfMolecule namesOfMolecule = db.fetchNamesFromInchi(inchi);
    if (namesOfMolecule == null) {
      LOGGER.debug("Molecule corresponding to %s was not found in the database. Skipping.", inchi);
      return;
    }
    // Chooses the best name according to Bing search results
    String bestName = bingSearchResults.findBestMoleculeName(namesOfMolecule);
    if (bestName.equals("")) { return; }

    // Get the total number of hits and the top search results
    Long totalCountSearchResults;
    Set<SearchResult> topSearchResults;
    if (cacheOnly) {
      totalCountSearchResults = bingSearchResults.getTotalCountSearchResultsFromCache(bestName);
      topSearchResults = bingSearchResults.getTopSearchResultsFromCache(bestName);
    } else {
      totalCountSearchResults = bingSearchResults.getAndCacheTotalCountSearchResults(bestName);
      topSearchResults = bingSearchResults.getAndCacheTopSearchResults(bestName);
    }
    NameSearchResults nameSearchResults = new NameSearchResults(bestName);
    nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
    nameSearchResults.setTopSearchResults(topSearchResults);

    // Intersect usage names with search results
    Set<UsageTermUrlSet> moleculeUsageTerms = new HashSet<>();
    for (String usageTerm : usageTerms) {
      UsageTermUrlSet usageTermUrlSet = new UsageTermUrlSet(usageTerm);
      usageTermUrlSet.populateUrlsFromNameSearchResults(nameSearchResults);
      if (usageTermUrlSet.getUrlSet().size() > 0) {
        moleculeUsageTerms.add(usageTermUrlSet);
      }
    }

    // Annotate the chemical with Bing Search Results
    BasicDBObject doc = db.createBingMetadataDoc(moleculeUsageTerms, totalCountSearchResults, bestName);
    db.updateChemicalWithBingSearchResults(inchi, bestName, doc);
  }

  public void addBingSearchResultsForInchiSet(Set<String> inchis) {

    LOGGER.info("Annotating %d chemicals with Bing Search results and usage terms.", inchis.size());
    int counter = 0;
    for (String inchi : inchis) {
      if (!forceUpdate && db.hasBingSearchResultsFromInchi(inchi)) {
        LOGGER.debug("Existing Bing search results found for %s. Skipping.", inchi);
        continue;
      }
      try {
        addBingSearchResultsForInChI(inchi);
      } catch (IOException e) {
        LOGGER.error("Could not add bing results for %s. Skipping.", inchi);
      }
      if (++counter % 100 == 0) {
        LOGGER.info("Added Bing Search results for %d chemicals (total %d)", inchis.size(), counter);
      }
    }
  }
}
