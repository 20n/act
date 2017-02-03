package act.installer.bing;

import act.server.BingCacheMongoDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpStatus;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * BingSearchResults provides methods for:
 * - querying the Bing Search API,
 * - caching its results in a Mongo database
 * - returning searched or cached results
 * - finding the best name for a molecule
 */

public class BingSearchResults {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchResults.class);

  // Full path to the account key for the Bing Search API (on the NAS)
  private static final String ACCOUNT_KEY_FILEPATH = "data/bing/bing_search_api_account_key.txt";
  // Maximum number of results possible per API call. This is the maximum value for URL parameter "count"
  private static final Integer MAX_RESULTS_PER_CALL = 50;
  // How many search results should be retrieved when getting topSearchResults
  private static final Integer TOP_N = 50;
  
  // The centralized location for caching Bing Search queries.
  // TODO: make this changeable without a code change (with CLI maybe?)
  private static final String BING_CACHE_HOST = "localhost";
  private static final int BING_CACHE_MONGO_PORT = 27777;
  private static final String BING_CACHE_MONGO_DATABASE = "bingsearch";


  private static final String BING_API_HOST = "api.cognitive.microsoft.com";
  private static final String BING_API_PATH = "/bing/v5.0/search";

  private static ObjectMapper mapper = new ObjectMapper();

  private BingCacheMongoDB bingCacheMongoDB;
  private BasicHttpClientConnectionManager basicConnManager;
  private String accountKey;
  private boolean cacheOnly;

  public BingSearchResults() {
    this(ACCOUNT_KEY_FILEPATH);
  }

  public BingSearchResults(boolean cacheOnly) {
    this.cacheOnly = cacheOnly;
    this.bingCacheMongoDB = new BingCacheMongoDB(BING_CACHE_HOST, BING_CACHE_MONGO_PORT, BING_CACHE_MONGO_DATABASE);
  }

  public BingSearchResults(String accountKeyFilepath) {
    this.cacheOnly = false;
    this.bingCacheMongoDB = new BingCacheMongoDB(BING_CACHE_HOST, BING_CACHE_MONGO_PORT, BING_CACHE_MONGO_DATABASE);
    this.basicConnManager = new BasicHttpClientConnectionManager();
    try {
      this.accountKey = getAccountKey(accountKeyFilepath);
    } catch (IOException e) {
      String msg = String.format("Bing Searcher could not find account key at %s", accountKeyFilepath);
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  /** This function gets the account key located on the NAS
   * @return the account key to be used for authentication purposes
   * @throws IOException
   */
  private static String getAccountKey(String accountKeyFilename) throws IOException {
    FileInputStream fs = new FileInputStream(accountKeyFilename);
    BufferedReader br = new BufferedReader(new InputStreamReader(fs));
    String account_key = br.readLine();
    return account_key;
  }

  /** This function fetches the total number of Bing search results and return the "totalCountSearchResult".
   * @param formattedName name that will be used as search query, lowercase formatted
   * @return the total count search results from Bing search
   * @throws IOException
   */
  private Long fetchTotalCountSearchResults(String formattedName) throws IOException {
    LOGGER.debug("Updating totalCountSearchResults for name: %s.", formattedName);
    final String queryTerm = URLEncoder.encode(formattedName, StandardCharsets.UTF_8.name());
    // Set count to 1 and offset to 0 since we need only one search result to extract the estimated count.
    final int count = 1;
    final int offset = 0;
    JsonNode results = fetchBingSearchAPIResponse(queryTerm, count, offset);
    return results.path("totalEstimatedMatches").asLong();
  }


  /** This function fetches the topN Bing search results for the current instance of NameSearchResult object
   * and updates the "topSearchResults" instance variable. Existing value is overridden.
   * @param formattedName name that will be used as search query, lowercase formatted
   * @param topN number of Web results to fetch from Bing Search API
   * @return returns a set of SearchResults containing the topN Bing search results
   * @throws IOException
   */
  private Set<SearchResult> fetchTopSearchResults(String formattedName, Integer topN)
      throws IOException {
    LOGGER.debug("Updating topSearchResults for name: %s.", formattedName);
    Set<SearchResult> topSearchResults = new HashSet<>();
    final String queryTerm = URLEncoder.encode(formattedName, StandardCharsets.UTF_8.name());
    // The Bing search API cannot return more than 100 results at once, but it is possible to iterate
    // through the results.
    // For example, if we need topN = 230 results, we will issue the following queries
    // (count and offset are URL parameters)
    // QUERY 1: count = 100, offset = 0
    // QUERY 2: count = 100, offset = 100
    // QUERY 3: count = 30, offset = 200
    Integer iterations = topN / MAX_RESULTS_PER_CALL;
    Integer remainder = topN % MAX_RESULTS_PER_CALL;
    for (int i = 0; i < iterations; i++) {
      topSearchResults.addAll(fetchSearchResults(queryTerm, MAX_RESULTS_PER_CALL, MAX_RESULTS_PER_CALL * i));
    }
    if (remainder > 0) {
      topSearchResults.addAll(fetchSearchResults(queryTerm, remainder, MAX_RESULTS_PER_CALL * iterations));
    }
    return topSearchResults;
  }


  /** This function issues a Bing Search API call and parses the response to extract a set of SearchResults.
   * @param query (String) the term to query for.
   * @param count (int) URL parameter indicating how many results to return. Max value is 100.
   * @param offset (int) URL parameter indicating the offset for results.
   * @return returns a set of SearchResults containing [count] search results with offset [offset]
   * @throws IOException
   */
  private Set<SearchResult> fetchSearchResults(String query, int count, int offset) throws IOException {
    if (count > MAX_RESULTS_PER_CALL) {
      LOGGER.warn("Number of results requested (%d) was too high. Will get only %d", count, MAX_RESULTS_PER_CALL);
    }
    Set<SearchResult> searchResults = new HashSet<>();
    JsonNode results = fetchBingSearchAPIResponse(query, count, offset);
    final JsonNode webResults = results.path("value");
    for (JsonNode webResult : webResults) {
      SearchResult searchResult = new SearchResult();
      searchResult.populateFromJsonNode(webResult);
      searchResults.add(searchResult);
    }
    return searchResults;
  }

  /** This function issues a Bing search API call and gets the JSONObject containing the relevant results
   * (including TotalCounts and SearchResults)
   * @param queryTerm (String) the term to query for.
   * @param count (int) URL parameter indicating how many results to return. Max value is 100.
   * @param offset (int) URL parameter indicating the offset for results.
   * @return a JSONObject containing the response.
   * @throws IOException
   */
  private JsonNode fetchBingSearchAPIResponse(String queryTerm, Integer count, Integer offset) throws IOException {

    if (count <= 0) {
      LOGGER.error("Bing Search API was called with \"count\" URL parameter = 0. Please request at least one result.");
      return null;
    }

    URI uri = null;
    try {
      // Bing URL pattern. Note that we use composite queries to allow retrieval of the total results count.
      // Transaction cost is [count] bings, where [count] is the value of the URL parameter "count".
      // In other words, we can make 5M calls with [count]=1 per month.


      // Example: https://api.cognitive.microsoft.com/bing/v5.0/search?q=porsche&responseFilter=webpages
      uri = new URIBuilder()
          .setScheme("https")
          .setHost(BING_API_HOST)
          .setPath(BING_API_PATH)
          // Wrap the query term (%s) with double quotes (%%22) for exact search
          .setParameter("q", String.format("%s", queryTerm))
          // Restrict response to Web Pages only
          .setParameter("responseFilter", "webpages")
          // "count" parameter.
          .setParameter("count", count.toString())
          // "offset" parameter.
          .setParameter("offset", offset.toString())
          .build();

    } catch (URISyntaxException e) {
      LOGGER.error("An error occurred when trying to build the Bing Search API URI", e);
    }

    JsonNode results;
    HttpGet httpget = new HttpGet(uri);
    // Yay for un-encrypted account key!
    // TODO: actually is there a way to encrypt it?
    httpget.setHeader("Ocp-Apim-Subscription-Key", accountKey);

    CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(basicConnManager).build();

    try (CloseableHttpResponse response = httpclient.execute(httpget)) {
      Integer statusCode = response.getStatusLine().getStatusCode();

      // TODO: The Web Search API returns useful error messages, we could use them to have better insights on failures.
      // See: https://dev.cognitive.microsoft.com/docs/services/56b43eeccf5ff8098cef3807/operations/56b4447dcf5ff8098cef380d
      if (!statusCode.equals(HttpStatus.SC_OK)) {
        LOGGER.error("Bing Search API call returned an unexpected status code (%d) for URI: %s", statusCode, uri);
        return null;
      }

      HttpEntity entity = response.getEntity();
      ContentType contentType = ContentType.getOrDefault(entity);
      Charset charset = contentType.getCharset();
      if (charset == null) {
        charset = StandardCharsets.UTF_8;
      }

      try (final BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent(), charset))) {
        String inputLine;
        final StringBuilder stringResponse = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
          stringResponse.append(inputLine);
        }
        JsonNode rootNode = mapper.readValue(stringResponse.toString(), JsonNode.class);
        results = rootNode.path("webPages");
      }
    }
    return results;
  }

  /** This key function caches in a MongoDB collection and returns a set of SearchResults.
   * If present, the results are returned from the cache. If not, the results are queried and returned after updating
   * the cache.
   * @param name (String) the name to return results for. Will be normalized to lower case.
   * @return a set of SearchResults
   * @throws IOException
   */
  public Set<SearchResult> getAndCacheTopSearchResults(String name) throws IOException {

    String formattedName = name.toLowerCase();
    BasicDBObject nameSearchResultDBObject = bingCacheMongoDB.getNameSearchResultDBObjectFromName(formattedName);
    Set<SearchResult> searchResults = new HashSet<>();

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the topSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the topSearchResults are not populated.
    //    This case occurs when only totalCountSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the results
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the results.

    if (nameSearchResultDBObject == null) {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      // Query the results
      searchResults = fetchTopSearchResults(formattedName, TOP_N);
      // Create new object and update it
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      nameSearchResults.setTopSearchResults(searchResults);
      // Save new document in the cache
      bingCacheMongoDB.cacheNameSearchResult(nameSearchResults);
      return searchResults;
    }

    // There is an existing entry in the DB
    BasicDBList topSearchResultsList = (BasicDBList) nameSearchResultDBObject.get("topSearchResults");
    if (topSearchResultsList == null) {
      // Case 2)
      LOGGER.debug("Existing entry in the cache, with empty topSearchResults. Fetching results and updating cache.");
      // Query the results
      searchResults = fetchTopSearchResults(formattedName, TOP_N);
      // Create new object and update its instance variable
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      nameSearchResults.setTopSearchResults(searchResults);
      // Update the cache
      bingCacheMongoDB.updateTopSearchResults(formattedName, nameSearchResults);
      return searchResults;
    }

    // Case 1)
    LOGGER.debug("Existing entry in the cache, with populated topSearchResults. Returning from the cache.");
    for (Object topSearchResult : topSearchResultsList) {
      SearchResult searchResult = new SearchResult();
      BasicDBObject topSearchResultDBObject = (BasicDBObject) topSearchResult;
      searchResult.populateFromBasicDBObject(topSearchResultDBObject);
      searchResults.add(searchResult);
    }
    return searchResults;
  }

  public Set<SearchResult> getTopSearchResultsFromCache(String name) {
    Set<SearchResult> searchResults = new HashSet<>();
    String formattedName = name.toLowerCase();
    BasicDBObject nameSearchResultDBObject = bingCacheMongoDB.getNameSearchResultDBObjectFromName(formattedName);
    if (nameSearchResultDBObject == null) {
      return searchResults;
    }
    BasicDBList topSearchResultsList = (BasicDBList) nameSearchResultDBObject.get("topSearchResults");
    if (topSearchResultsList == null) {
      return searchResults;
    }
    for (Object topSearchResult : topSearchResultsList) {
      SearchResult searchResult = new SearchResult();
      BasicDBObject topSearchResultDBObject = (BasicDBObject) topSearchResult;
      searchResult.populateFromBasicDBObject(topSearchResultDBObject);
      searchResults.add(searchResult);
    }
    return searchResults;
  }

  public Long getTotalCountSearchResultsFromCache(String name) {
    String formattedName = name.toLowerCase();
    BasicDBObject nameSearchResultDBObject = bingCacheMongoDB.getNameSearchResultDBObjectFromName(formattedName);
    Long totalCountSearchResults;
    if (nameSearchResultDBObject == null) {
      return -1L;
    }
    totalCountSearchResults = (Long) nameSearchResultDBObject.get("totalCountSearchResults");
    if (totalCountSearchResults == null) {
      return -1L;
    }
    return totalCountSearchResults;
  }

  /** This key function caches in a MongoDB collection and returns the total count of Bing search results.
   * If present, the results are returned from the cache. If not, the results are queried and returned after updating
   * the cache.
   * @param name (String) the name to return results for. Will be normalized to lower case.
   * @return the total search result count
   * @throws IOException
   */
  public Long getAndCacheTotalCountSearchResults(String name) throws IOException {

    String formattedName = name.toLowerCase();
    BasicDBObject nameSearchResultDBObject = bingCacheMongoDB.getNameSearchResultDBObjectFromName(formattedName);
    Long totalCountSearchResults;

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the totalCountSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the totalCountSearchResults are not populated.
    //    This case occurs when only topSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the total count
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the total count.

    if (nameSearchResultDBObject == null) {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      // Query the results
      totalCountSearchResults = fetchTotalCountSearchResults(formattedName);
      // Create new object and update it
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
      // Save new document in the cache
      bingCacheMongoDB.cacheNameSearchResult(nameSearchResults);
      return totalCountSearchResults;
    }

    // There is an existing entry in the cache
    totalCountSearchResults = (Long) nameSearchResultDBObject.get("totalCountSearchResults");

    if (totalCountSearchResults == null || totalCountSearchResults < 0) {
      // Case 2)
      LOGGER.debug("Existing entry in the cache, with empty totalCountSearchResults. " +
          "Fetching results and updating cache.");
      // Query the results
      totalCountSearchResults = fetchTotalCountSearchResults(formattedName);
      // Create new object and update its instance variable
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
      // Update the cache
      bingCacheMongoDB.updateTotalCountSearchResults(formattedName, nameSearchResults);
      return totalCountSearchResults;
    }

    // Case 1)
    LOGGER.debug("Existing entry in the cache, with populated totalCountSearchResults. Returning from the cache.");
    return totalCountSearchResults;
  }

  /** Heuristic to find the best name for a given InChI, based on the total number of search results
   * @param namesOfMolecule (NamesOfMolecule) Java object containing Brenda, MetaCyc, ChEBI and DrugBank names for a given
   *                      InChI.
   * @return the name with the highest total number of search results, called Best Name
   * @throws IOException
   */
  public String findBestMoleculeName(NamesOfMolecule namesOfMolecule) throws IOException {
    Long maxCount = -1L;
    String bestName = "";
    String inchi = namesOfMolecule.getInchi();
    String[] splittedInchi = inchi.split("/");
    String formulaFromInchi = null;
    if (splittedInchi.length >= 2) {
      formulaFromInchi = inchi.split("/")[1];
    }

    LOGGER.debug("Formula %s extracted from %s", formulaFromInchi, inchi);

    String wikipediaName = namesOfMolecule.getWikipediaName();
    if (wikipediaName != null) {
      bestName = wikipediaName;
    } else {
      Set<String> names = namesOfMolecule.getAllNames();
      names.remove(formulaFromInchi);
      for (String name : names) {
        // Ignore name if <= 4 characters
        if (name.length() <= 4) {
          continue;
        }
        LOGGER.debug("Getting search hits for %s", name);
        Long count = (cacheOnly) ? getTotalCountSearchResultsFromCache(name) : getAndCacheTotalCountSearchResults(name);
        // Ignore name if there was a previous better candidate
        if (count <= maxCount) {
          continue;
        }
        maxCount = count;
        bestName = name;
      }
    }

    // Note we don't use ChEBI or DrugBank names to keep this function simple.
    // If Brenda and MetaCyc names are not populated, it is very rare that ChEBI or DrugBank would be.
    LOGGER.debug("Best name found for %s is %s", namesOfMolecule.getInchi(), bestName);
    return bestName;
  }

  public static void main(String[] args) {
    String apiKeyFilepath = "/mnt/shared-data-1/Thomas/test-bing/microsoft-cognitive-service-api-key";
    BingSearchResults bingSearchResults = new BingSearchResults(apiKeyFilepath);
    try {
      Set<SearchResult> res = bingSearchResults.getAndCacheTopSearchResults("new query");
      Long count = bingSearchResults.getAndCacheTotalCountSearchResults("new query");
    } catch (IOException e) {
      throw new RuntimeException("Exception occurred when computing example query, %s", e);
    }
  }
}
