package act.installer.bing;

import act.server.BingCacheMongoDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
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
import java.util.Base64;
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
  // TODO: update this to pass the account key as a configuration parameter
  private static final String ACCOUNT_KEY_FILENAME = "/Volumes/data-level1/data/bing/bing_search_api_account_key.txt";
  // Maximum number of results possible per API call. This is the maximum value for URL parameter "top"
  private static final Integer MAX_RESULTS_PER_CALL = 100;
  // How many search results should be retrieved when getting topSearchResults
  private static final Integer TOP_N = 50;
  
  // The centralized location for caching Bing Search queries.
  private static final String BING_CACHE_HOST = "10.0.20.19"; // Chimay IP address. Replace when we have a DNS system.
  private static final int BING_CACHE_MONGO_PORT = 27777;
  private static final String BING_CACHE_MONGO_DATABASE = "bingsearch";

  private static ObjectMapper mapper = new ObjectMapper();

  private BingCacheMongoDB bingCacheMongoDB;
  private BasicHttpClientConnectionManager basicConnManager;
  private HttpClientContext context;

  public BingSearchResults() {
    bingCacheMongoDB = new BingCacheMongoDB(BING_CACHE_HOST, BING_CACHE_MONGO_PORT, BING_CACHE_MONGO_DATABASE);
    basicConnManager = new BasicHttpClientConnectionManager();
    context = HttpClientContext.create();
  }

  /** This function gets the account key located on the NAS
   * @return the encoded account key to be used for authentication purposes
   * @throws IOException
   */
  private String getAccountKeyEncoded() throws IOException {
    // Account key linked with Chris' Bing API account (chris@20n.com)
    // Monthly transactions allowed: 100,000 (or 5M bings). WebSearch use only.
    FileInputStream fs= new FileInputStream(ACCOUNT_KEY_FILENAME);
    BufferedReader br = new BufferedReader(new InputStreamReader(fs));
    String account_key = br.readLine();

    // Encrypted account key
    String account_key_enc = Base64.getEncoder().encodeToString((account_key + ":" + account_key).getBytes());
    return account_key_enc;
  }

  /** This function fetches the total number of Bing search results and return the "totalCountSearchResult".
   * @param formattedName name that will be used as search query, lowercase formatted
   * @return the total count search results from Bing search
   * @throws IOException
   */
  private Long fetchTotalCountSearchResults(String formattedName) throws IOException {
    LOGGER.debug("Updating totalCountSearchResults for name: %s.", formattedName);
    final String queryTerm = URLEncoder.encode(formattedName, StandardCharsets.UTF_8.name());
    final int top = 1;
    final int skip = 0;
    JsonNode results = fetchBingSearchAPIResponse(queryTerm, top, skip);
    String WebTotal = results.path("WebTotal").asText();
    return Long.parseLong(WebTotal);
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
    // (top and skip are URL parameters)
    // QUERY 1: top = 100, skip = 0
    // QUERY 2: top = 100, skip = 100
    // QUERY 3: top = 30, skip = 200
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
   * @param top (int) URL parameter indicating how many results to return. Max value is 100.
   * @param skip (int) URL parameter indicating the offset for results. Has to comply with top + skip <= 1000.
   * @return returns a set of SearchResults containing [top] search results with offset [skip]
   * @throws IOException
   */
  private Set<SearchResult> fetchSearchResults(String query, int top, int skip) throws IOException {
    Set<SearchResult> searchResults = new HashSet<>();
    JsonNode results = fetchBingSearchAPIResponse(query, top, skip);
    final JsonNode webResults = results.path("Web");
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
   * @param top (int) URL parameter indicating how many results to return. Max value is 100.
   * @param skip (int) URL parameter indicating the offset for results. Has to comply with top + skip <= 1000.
   * @return a JSONObject containing the response.
   * @throws IOException
   */
  private JsonNode fetchBingSearchAPIResponse(String queryTerm, Integer top, Integer skip) throws IOException {

    if (top <= 0) {
      LOGGER.error("Bing Search API was called with \"top\" URL parameter = 0. Please request at least one result.");
      return null;
    }

    URI uri = null;
    try {
      // Bing URL pattern. Note that we use composite queries to allow retrieval of the total results count.
      // Transaction cost is [top] bings, where [top] is the value of the URL parameter "top".
      // In other words, we can make 5M calls with [top]=1 per month.
      uri = new URIBuilder()
          .setScheme("https")
          .setHost("api.datamarket.azure.com")
          .setPath("/Bing/SearchWeb/Composite")
          // Wrap the query term (%s) with double quotes (%%22) for exact search
          .setParameter("Query", String.format("'\"%s\"'", queryTerm))
          // "top" parameter. Integer between 1 and 100.
          .setParameter("$top", top.toString())
          // "skip" parameter. Integer, satisfying the constraint: [top] + [skip] <= 1000.
          .setParameter("$skip", skip.toString())
          // Disable query alterations to make sure we query the exact string provided
          .setParameter("WebSearchOptions", "'DisableQueryAlterations'")
          .setParameter("$format", "JSON")
          .build();

    } catch (URISyntaxException e) {
      LOGGER.error("An error occurred when trying to build the Bing Search API URI", e);
    }

    JsonNode results;
    HttpGet httpget = new HttpGet(uri);
    httpget.setHeader("Authorization", "Basic " + getAccountKeyEncoded());

    CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(basicConnManager).build();

    try (CloseableHttpResponse response = httpclient.execute(httpget)) {
      Integer statusCode = response.getStatusLine().getStatusCode();

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
        results = rootNode.path("d").path("results").path(0);
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
      Set<String> names = namesOfMolecule.getPrimaryNames();
      if (names.size() > 0) {
        names.remove(formulaFromInchi);
      } else {
        names = namesOfMolecule.getAlternateNames();
        LOGGER.info("Alternate names %s were selected for %s", names.toString(), inchi);
      }
      for (String name : names) {
        // Ignore name if <= 4 characters
        if (name == null || name.length() <= 4) {
          continue;
        }
        LOGGER.debug("Getting search hits for %s", name);
        Long count = getAndCacheTotalCountSearchResults(name);
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
}
