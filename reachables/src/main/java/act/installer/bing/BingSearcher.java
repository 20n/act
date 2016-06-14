package act.installer.bing;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import act.server.DBIterator;
import act.shared.Chemical;
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

  public void addBingSearchResults(MongoDB db) throws IOException {

    // Get the usage terms
    LOGGER.debug("Getting usage terms corpus.");
    UsageTermsCorpus usageTermsCorpus = new UsageTermsCorpus(USAGE_TERMS_FILENAME);
    usageTermsCorpus.buildCorpus();
    Set<String> usageTerms = usageTermsCorpus.getUsageTerms();

    LOGGER.debug("Annotating chemicals with Bing Search results and usage terms.");
    BingSearchResults bingSearchResults = new BingSearchResults();

    DBIterator chemicalsIterator = db.getIteratorOverChemicals();
    // Iterate over all chemicals
    while (chemicalsIterator.hasNext()) {
      Chemical chemical = db.getNextChemical(chemicalsIterator);
      String inchi = chemical.getInChI();

      if (db.hasBingSearchResultsFromInchi(inchi)) {
        LOGGER.debug("Existing Bing search results found for %s. Skipping.", inchi);
        continue;
      }

      LOGGER.debug("Processing InChI " + inchi);
      // Fetches the names (Brenda, Metacyc, Chebi, Drugbank)
      NamesOfMolecule namesOfMolecule = db.fetchNamesFromInchi(inchi);
      // Chooses the best name according to Bing search results
      String bestName = bingSearchResults.findBestMoleculeName(namesOfMolecule);
      if (bestName.equals("")) {continue;}

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
  }
}
