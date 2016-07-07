package act.installer.bing;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.mongodb.BasicDBObject;

import act.server.MongoDB;

/**
 * This class contains the main logic for installing Bing Search results in the Installer DB
 */

public class BingSearcher {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearcher.class);
  static final private String USAGE_TERMS_FILENAME = "usage_terms.txt";

  public BingSearcher() {
  }

  private void addBingSearchResultsForInChI(MongoDB db,
                                           BingSearchResults bingSearchResults,
                                           String inchi,
                                           Set<String> usageTerms) throws IOException {
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
    Long totalCountSearchResults = bingSearchResults.getAndCacheTotalCountSearchResults(bestName);
    Set<SearchResult> topSearchResults = bingSearchResults.getAndCacheTopSearchResults(bestName);
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

  public void addBingSearchResultsForInchiSet(MongoDB db, Set<String> inchis, Boolean forceUpdate)
      throws IOException {

    // Get the usage terms
    LOGGER.debug("Getting usage terms corpus.");
    UsageTermsCorpus usageTermsCorpus = new UsageTermsCorpus(USAGE_TERMS_FILENAME);
    usageTermsCorpus.buildCorpus();
    Set<String> usageTerms = usageTermsCorpus.getUsageTerms();

    LOGGER.debug("Annotating chemicals with Bing Search results and usage terms.");
    BingSearchResults bingSearchResults = new BingSearchResults();

    for (String inchi : inchis) {
      if (!forceUpdate && db.hasBingSearchResultsFromInchi(inchi)) {
        LOGGER.debug("Existing Bing search results found for %s. Skipping.", inchi);
        continue;
      }
      addBingSearchResultsForInChI(db, bingSearchResults, inchi, usageTerms);
    }
  }
}
