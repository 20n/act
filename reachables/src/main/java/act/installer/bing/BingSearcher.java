package act.installer.bing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

import act.installer.wikipedia.ImportantChemicalsWikipedia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.AbstractWell;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

import com.act.utils.TSVParser;

import act.server.MongoDB;


public class BingSearcher {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearcher.class);
  static final private String USAGE_TERMS_FILENAME = "usage_terms.txt";

  public BingSearcher() {
  }

  public void addBingSearchResults(MongoDB db) throws IOException {
    BingSearchResults bingSearchResults = new BingSearchResults();
    LOGGER.debug("Annotating chemicals with Bing Search results and usage terms.");
    Map<String, Long> all_db_chems = db.constructAllInChIs();

    // Get the usage terms
    UsageTermsCorpus usageTermsCorpus = new UsageTermsCorpus(USAGE_TERMS_FILENAME);
    usageTermsCorpus.buildCorpus();
    HashSet<String> usageTerms = usageTermsCorpus.getUsageTerms();

    // Iterate over all chemicals
    for (String inchi : all_db_chems.keySet()) {
      if (db.hasBingSearchResultsFromInchi(inchi)) {
        LOGGER.debug("Existing Bing search results found for %s. Skipping.", inchi);
      } else {
        LOGGER.debug("Processing InChI " + inchi);
        // Fetches the names (Brenda, Metacyc, Chebi, Drugbank)
        MoleculeNames moleculeNames = db.fetchNamesFromInchi(inchi);
        // Chooses the best name according to Bing search results
        String bestName = bingSearchResults.getBestName(moleculeNames);
        if (!bestName.equals("")) {

          // Get the total number of hits and the top search results
          Long totalCountSearchResults = bingSearchResults.getAndCacheTotalCountSearchResults(bestName);
          HashSet<SearchResult> topSearchResults = bingSearchResults.getAndCacheTopSearchResults(bestName);
          NameSearchResults nameSearchResults = new NameSearchResults(bestName);
          nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
          nameSearchResults.setTopSearchResults(topSearchResults);

          // Intersect usage names with search results
          HashSet<UsageTermUrlSet> moleculeUsageTerms = new HashSet<>();
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
  }

  public HashSet<String> getStringSetFromFile(String filename) throws IOException {
    HashSet<String> strings = new HashSet<>();
    try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = br.readLine()) != null) {
        strings.add(line);
      }
    }
    return strings;
  }


  public HashSet<String> readInchisFromTSV(String inputPath) throws IOException {
    HashSet<String> inchiList = new HashSet<>();
    TSVParser parser = new TSVParser();
    parser.parse(new File(inputPath));
    List<Map<String, String>> results = parser.getResults();
    for (Map<String, String> result : results) {
      inchiList.add(result.get("inchi"));
    }
    return inchiList;
  }

  public static void main(final String[] args) throws IOException {
    BingSearcher bingSearcher = new BingSearcher();
    MongoDB db = new MongoDB("localhost", 27017, "actv01");
    bingSearcher.addBingSearchResults(db);
  }
}
