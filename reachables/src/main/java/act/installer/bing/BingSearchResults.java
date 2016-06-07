package act.installer.bing;

import act.server.MongoDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;


public class BingSearchResults {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchResults.class);

  private static ObjectMapper mapper = new ObjectMapper();

  // Account key linked with Chris' Bing API account (chris@20n.com)
  // Monthly transactions allowed: 100,000 (or 5M bings). WebSearch use only.
  static final private String ACCOUNT_KEY = "X1jVHh9GCbaLeVaKfe3p1GbmD0gjjdtNwii9d7jJ0nE=";
  // Encrypted account key
  static final private String ACCOUNT_KEY_ENC = Base64.getEncoder().encodeToString((ACCOUNT_KEY + ":" + ACCOUNT_KEY).getBytes());


  // Maximum number of results possible per API call. This is the maximum value for URL parameter "top"
  static final private Integer MAX_RESULTS_PER_CALL = 100;
  // How many search results should be retrieved when getting topSearchResults
  static final private Integer TOP_N = 50;

  // Mongo database collection where to cache the results.
  static private DBCollection bingCacheCollection;

  static final private int MONGO_PORT = 27717;
  static final private String MONGO_DATABASE = "bingsearch";
  static final private String MONGO_COLLECTION = "cache";

  public BingSearchResults() throws IOException {
    MongoClient mongo = new MongoClient("localhost", MONGO_PORT);
    DB db = mongo.getDB(MONGO_DATABASE);
    bingCacheCollection = db.getCollection(MONGO_COLLECTION);
  }


  /** This function fetches the total number of Bing search results and updates the "totalCountSearchResult"
   *  instance variable. Existing value is overridden.
   * @throws IOException
   */
  public Long fetchTotalCountSearchResults(NameSearchResults nameSearchResults) throws IOException, URISyntaxException {
    String name = nameSearchResults.getName();
    LOGGER.debug(String.format("Updating totalCountSearchResults for name: %s.", name));
    final String queryTerm = URLEncoder.encode(name, StandardCharsets.UTF_8.name());
    final int top = 1;
    final int skip = 0;
    JsonNode results = fetchBingSearchAPIResponse(queryTerm, top, skip);
    String WebTotal = results.path("WebTotal").asText();
    return Long.parseLong(WebTotal);
  }


  /** This function fetches the topN Bing search results for the current instance of NameSearchResult object
   * and updates the "topSearchResults" instance variable. Existing value is overridden.
   * @param topN Integer, number of Web results to fetch from Bing Search API
   * @throws IOException
   */
  public HashSet<SearchResult> fetchTopSearchResults(NameSearchResults nameSearchResults, Integer topN) throws IOException, URISyntaxException {
    String name = nameSearchResults.getName();
    LOGGER.debug(String.format("Updating topSearchResults for name: %s.", name));
    HashSet<SearchResult> topSearchResults = new HashSet<>();
    final String queryTerm = URLEncoder.encode(name, StandardCharsets.UTF_8.name());
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
   * @return returns a set of SearchResults containing the relevant information
   * @throws IOException
   */
  private HashSet<SearchResult> fetchSearchResults(String query, int top, int skip)
      throws IOException, URISyntaxException {
    HashSet<SearchResult> searchResults = new HashSet<>();
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
  public JsonNode fetchBingSearchAPIResponse(String queryTerm, Integer top, Integer skip) throws IOException, URISyntaxException {

    if (top <= 0) {
      LOGGER.error("Bing Search API was called with \"top\" URL parameter = 0. Please request at least one result.");
      return null;
    }

    // Bing URL pattern. Note that we use composite queries to allow retrieval of the total results count.
    // Transaction cost is [top] bings, where [top] is the value of the URL parameter "top".
    // In other words, we can make 5M calls with [top]=1 per month.
    final URI uri = new URIBuilder()
        .setScheme("https")
        .setHost("api.datamarket.azure.com")
        .setPath("/Bing/SearchWeb/Composite")
        // Wrap the query term (%s) with double quotes (%%22) for exact search
        .setParameter("Query", String.format("%%27%%22%s%%22%%27", queryTerm))
        // "top" parameter. Integer between 1 and 100.
        .setParameter("top", top.toString())
        // "skip" parameter. Integer, satisfying the constraint: [top] + [skip] <= 1000.
        .setParameter("skip", skip.toString())
        // Disable query alterations to make sure we query the exact string provided
        .setParameter("WebSearchOptions", "%%27DisableQueryAlterations%%27")
        .setParameter("format", "JSON")
        .build();

    JsonNode results;
    HttpGet httpget = new HttpGet(uri);
    httpget.setHeader("Authorization", "Basic " + ACCOUNT_KEY_ENC);

    CloseableHttpClient httpclient = HttpClients.createDefault();
    CloseableHttpResponse response = httpclient.execute(httpget);
    InputStream inputStream = response.getEntity().getContent();

    try (final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
      String inputLine;
      final StringBuilder stringResponse = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        stringResponse.append(inputLine);
      }
      JsonNode rootNode = mapper.readValue(response.toString(), JsonNode.class);
      results = rootNode.path("d").path("results").path(0);
    }
    return results;
  }

  /** This key function caches in a MongoDB collection and returns a set of SearchResults.
   * If present, the results are returned from the cache. If not, the results are queried and returned after updating
   * the cache.
   * @param formattedName (String) the name to return results for. Will be normalized to lower case.
   * @return a set of SearchResults
   * @throws IOException
   */
  public HashSet<SearchResult> getAndCacheTopSearchResults(MongoDB db, String formattedName) throws IOException, URISyntaxException {

    BasicDBObject nameSearchResultDBObject = db.getNameSearchResultDBObjectFromName(formattedName);

    HashSet<SearchResult> searchResults = new HashSet<>();

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the topSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the topSearchResults are not populated.
    //    This case occurs when only totalCountSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the results
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the results.

    if (nameSearchResultDBObject != null) {
      // There is an existing entry in the DB
      BasicDBList topSearchResultsList = (BasicDBList) nameSearchResultDBObject.get("topSearchResults");
      if (topSearchResultsList != null) {
        // Case 1)
        LOGGER.debug("Existing entry in the cache, with populated topSearchResults. Returning from the cache.");
        for (Object topSearchResult : topSearchResultsList) {
          SearchResult searchResult = new SearchResult();
          BasicDBObject topSearchResultDBObject = (BasicDBObject) topSearchResult;
          searchResult.populateFromBasicDBObject(topSearchResultDBObject);
          searchResults.add(searchResult);
        }
        return searchResults;
      } else {
        // Case 2)
        LOGGER.debug("Existing entry in the cache, with empty topSearchResults. Fetching results and updating cache.");
        NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
        // Query the results
        searchResults = fetchTopSearchResults(nameSearchResults, TOP_N);
        nameSearchResults.setTopSearchResults(searchResults);
        db.updateTopSearchResults(formattedName, nameSearchResults);
        return searchResults;
      }
    } else {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      // Query the results
      searchResults = fetchTopSearchResults(nameSearchResults, TOP_N);
      nameSearchResults.setTopSearchResults(searchResults);
      db.cacheNameSearchResult(nameSearchResults);
      return searchResults;
    }
  }

  /** This key function caches in a MongoDB collection and returns the total count of Bing search results.
   * If present, the results are returned from the cache. If not, the results are queried and returned after updating
   * the cache.
   * @param formattedName (String) the name to return results for. Will be normalized to lower case.
   * @return the total search result count
   * @throws IOException
   */
  public Long getAndCacheTotalCountSearchResults(MongoDB db, String formattedName) throws IOException, URISyntaxException {

    BasicDBObject nameSearchResultDBObject = db.getNameSearchResultDBObjectFromName(formattedName);
    Long totalCountSearchResults;

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the totalCountSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the totalCountSearchResults are not populated.
    //    This case occurs when only topSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the total count
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the total count.

    if (nameSearchResultDBObject != null) {
      // There is an existing entry in the cache
      totalCountSearchResults = (Long) nameSearchResultDBObject.get("totalCountSearchResults");
      if (totalCountSearchResults != null && totalCountSearchResults >= 0) {
        // Case 1)
        LOGGER.debug("Existing entry in the cache, with populated totalCountSearchResults. Returning from the cache.");
        return totalCountSearchResults;
      } else {
        // Case 2)
        LOGGER.debug("Existing entry in the cache, with empty totalCountSearchResults. " +
            "Fetching results and updating cache.");
        NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
        // Query the results
        totalCountSearchResults = fetchTotalCountSearchResults(nameSearchResults);
        nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
        db.updateTotalCountSearchResults(formattedName, nameSearchResults);
        return totalCountSearchResults;
      }
    } else {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      // Query the results
      totalCountSearchResults = fetchTotalCountSearchResults(nameSearchResults);
      nameSearchResults.setTotalCountSearchResults(totalCountSearchResults);
      // Save new document in the cache
      db.cacheNameSearchResult(nameSearchResults);
      return totalCountSearchResults;
    }
  }



}
