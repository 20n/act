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
  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static final private Integer TOP = 1;

  static final private String USAGE_TERMS_PATH = "src/main/resources/search_terms.txt";

  static final private String PABA_CLADE_INCHIS_PATH = "src/main/resources/PABA-clade-112-onchis.txt";

  static final private String BENZENE_RING_INCHIS = "src/main/resources/benzene_search_results_20160606T0937.contains_benzene.txt";

  static final private String SHIKIMATE_CLADE_INCHIS = "src/main/resources/Inchis_shikimate.txt";



  static final private String TSV_SEPARATOR = "\t";

  private static ObjectMapper mapper = new ObjectMapper();

  public BingSearcher() {
  }

  public class UsageTerms {
    private HashSet<String> usageTermsSet = new HashSet<>();

    public HashSet<String> getUsageTerms(String filename) throws IOException {
      try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
        String line;
        while ((line = br.readLine()) != null) {
          usageTermsSet.add(line);
        }
      }
      return usageTermsSet;
    }
  }

  public class MoleculeUsageTerm {
    private String usageTerm;
    private HashSet<String> urlSet = new HashSet<>();

    public MoleculeUsageTerm(String usageTerm) {
      this.usageTerm = usageTerm;
    }

    public void populateUrlsFromNameSearchResults(NameSearchResults nameSearchResults) {
      HashSet<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
      if (topSearchResults != null) {
        for (SearchResult searchResult : topSearchResults) {
          String description = searchResult.getDescription();
          if (description.contains(usageTerm)) {
            urlSet.add(searchResult.getUrl());
          }
        }
      }
    }

    public BasicDBObject getBasicDBObject() {
      BasicDBObject o = new BasicDBObject();
      o.put("usage_term", usageTerm);
      BasicDBList l = new BasicDBList();
      for (String url : urlSet) {
        l.add(url);
      }
      o.put("urls", l);
      return o;
    }

    public String getUsageTerm() {
      return usageTerm;
    }

    public HashSet<String> getUrlSet() {
      return urlSet;
    }
  }

  public void addBingSearchResults(MongoDB db) throws IOException {
    BingSearchResults bingSearchResults = new BingSearchResults();
    System.out.println("reading all chemicals that will be bing searched");
    Map<String, Long> all_db_chems = db.constructAllInChIs();
    // HashSet<String> pabaCladeInchis = getStringSetFromFile(PABA_CLADE_INCHIS_PATH);
    // HashSet<String> benzeneRingInchis = readInchisFromTSV(BENZENE_RING_INCHIS);
    HashSet<String> shikimateCladeInchis = getStringSetFromFile(SHIKIMATE_CLADE_INCHIS);

    for (String inchi : all_db_chems.keySet()) {
      if (shikimateCladeInchis.contains(inchi)) {
        LOGGER.debug("Processing InChI " + inchi);
        // Fetches the names (Brenda, Metacyc, Chebi, Drugbank)
        MoleculeNames moleculeNames = db.fetchNamesFromInchi(inchi);
        // Chooses the best name according to Bing search results
        String bestName = bingSearchResults.getBestName(moleculeNames);
        if (bestName != "") {
          // Get the total number of hits and the top search results
          Long totalCountSearchResults = bingSearchResults.getAndCacheTotalCountSearchResults(bestName);
          HashSet<SearchResult> topSearchResults = bingSearchResults.getAndCacheTopSearchResults(bestName);
          NameSearchResults nameSearchResults = new NameSearchResults(bestName);
          nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
          nameSearchResults.setTopSearchResults(topSearchResults);
          // Get the usage terms
          UsageTerms usageTerms = new UsageTerms();
          HashSet<String> usageTermsSet = usageTerms.getUsageTerms(USAGE_TERMS_PATH);
          // Intersect usage names with search results
          HashSet<MoleculeUsageTerm> moleculeUsageTerms = new HashSet<>();
          for (String usageTerm : usageTermsSet) {
            MoleculeUsageTerm moleculeUsageTerm = new MoleculeUsageTerm(usageTerm);
            moleculeUsageTerm.populateUrlsFromNameSearchResults(nameSearchResults);
            if (moleculeUsageTerm.getUrlSet().size() > 0) {
              moleculeUsageTerms.add(moleculeUsageTerm);
            }
          }
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

/*

  public HashMap<String, HashSet<String>> getSearchMatches(
      String queryTerm, HashSet<String> usageTerms, Integer top, Integer skip)
      throws IOException {

    final String query = URLEncoder.encode(queryTerm, Charset.defaultCharset().name());
    final String bingUrl = String.format(BING_URL_PATTERN, query, top, skip);

    final URL url = new URL(bingUrl);
    connection = url.openConnection();
    connection.setRequestProperty("Authorization", "Basic " + ACCOUNT_KEY_ENC);

    HashMap<String, HashSet<String>> searchMatches =  new HashMap<>();

    try (final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
      String inputLine;
      final StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      final JSONObject json = new JSONObject(response.toString());
      final JSONObject d = json.getJSONObject("d");
      final JSONArray results = d.getJSONArray("results");
      final JSONObject res = results.getJSONObject(0);
      final JSONArray webResults = res.getJSONArray("Web");
      final int resultsLength = webResults.length();
      for (int i = 0; i < resultsLength; i++) {
        final JSONObject aResult = webResults.getJSONObject(i);
        String rawDescription = (String) aResult.get("Description");
        String description = rawDescription.toLowerCase();
        for (String usageTerm : usageTerms) {
          if (description.contains(usageTerm)) {
            HashSet<String> urlSet = searchMatches.get(usageTerm);
            if (urlSet == null) {
              urlSet = new HashSet<>();
              searchMatches.put(usageTerm, urlSet);
            }
            urlSet.add((String) aResult.get("Url"));
          }
        }
      }
    }
    return searchMatches;
  }


*/

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



  public void writeToTSV(String outputPath, HashSet<MoleculeNames> molecules)
      throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
    CSVPrinter printer = new CSVPrinter(writer, TSV_FORMAT);
    for (MoleculeNames moleculeNames : molecules) {
      List<String> nextLine = new ArrayList<>();
      nextLine.add(moleculeNames.getInchi());
      nextLine.add(mapper.writeValueAsString(moleculeNames.getBrendaNames()));
      nextLine.add(mapper.writeValueAsString(moleculeNames.getMetacycNames()));
      nextLine.add(mapper.writeValueAsString(moleculeNames.getChebiNames()));
      nextLine.add(mapper.writeValueAsString(moleculeNames.getDrugbankNames()));
      printer.printRecord(nextLine);
    }
    printer.flush();
    writer.close();
  }





  public static void main(final String[] args) throws IOException {

    BingSearcher bingSearcher = new BingSearcher();
    MongoDB db = new MongoDB("localhost", 27717, "actv01");

    // MoleculeNames moleculeNames = new MoleculeNames("InChI=1S/C6H7NO4S/c7-5-3-1-2-4-6(5)11-12(8,9)10/h1-4H,7H2,(H,8,9,10)/p-1");

    bingSearcher.addBingSearchResults(db);
  }
}


/*
    BingSearcher bingSearcher = new BingSearcher();

    BingSearchResults bingSearchResults = new BingSearchResults();

    MongoClient mongo = new MongoClient("localhost", 27717);
    DB db = mongo.getDB("actv01");
    DBCollection collection = db.getCollection("chemicals");

    /* HashSet<String> pabaCladeInchis = bingSearcher.getStringSetFromFile(PABA_CLADE_INCHIS_PATH);
    HashSet<MoleculeNames> pabaCladeMolecules = new HashSet<>();

    for (String pabaCladeInchi : pabaCladeInchis) {
      MoleculeNames pabaCladeMolNames = new MoleculeNames(pabaCladeInchi);
      pabaCladeMolNames.fetchNames(collection, LOGGER);
      pabaCladeMolecules.add(pabaCladeMolNames);
    }

    for (MoleculeNames moleculeNames : pabaCladeMolecules) {
      String bestName = bingSearcher.getBestName(moleculeNames);
      bingSearchResults.getAndCacheTopSearchResults(bestName);
    }
*/
// bingSearcher.writeToTSV("src/paba-clade-names.tsv", pabaCladeMolecules);
