package act.installer.bing;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashSet;

public class BingSearchResults {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchResults.class);

  // Account key linked with Chris' Bing API account (chris@20n.com)
  // Monthly transactions allowed: 100,000 (or 5M bings). WebSearch use only.
  static final private String ACCOUNT_KEY = "X1jVHh9GCbaLeVaKfe3p1GbmD0gjjdtNwii9d7jJ0nE=";
  // Encrypted account key
  static final private String ACCOUNT_KEY_ENC = Base64.getEncoder().encodeToString((ACCOUNT_KEY + ":" + ACCOUNT_KEY).getBytes());
  // Bing URL pattern. Note that we use composite queries to allow retrieval of the total results count.
  // Transaction cost is [top] bings, where [top] is the value of the URL parameter "top".
  // In other words, we can make 5M calls with [top]=1 per month.
  static final private String BING_URL_PATTERN =
      "https://api.datamarket.azure.com/Bing/SearchWeb/Composite?" +
          // Wrap the query term (%s) with double quotes (%%22) for exact search
          "Query=%%27%%22%s%%22%%27" + "&" +
          "$format=JSON" + "&" +
          // "top" parameter. Integer between 0 and 100.
          "$top=%d" + "&" +
          // "skip" parameter. Integer, satisfying the constraint: [top] + [skip] <= 1000.
          "$skip=%d" + "&" +
          // Disable query alterations to make sure we query the exact string provided
          "WebSearchOptions=%%27DisableQueryAlterations%%27";
  // Maximum number of results possible per API call. This is the maximum value for URL parameter "top"
  static final private Integer MAX_RESULTS_PER_QUERY = 100;
  // How many search results should be retrieved when getting topSearchResults
  static final private Integer TOP_N = 50;

  // Mongo database collection where to cache the results.
  private DBCollection bingCacheCollection;

  public BingSearchResults() throws IOException {
    MongoClient mongo = new MongoClient("localhost", 27717);
    DB db = mongo.getDB("actv01");
    bingCacheCollection = db.getCollection("bingCache");
  }

  public class NameSearchResults {
    private String name;
    private Long totalCountSearchResults = new Long(-1);
    private HashSet<SearchResult> topSearchResults = null;

    public NameSearchResults(String name) {
      this.name = name.toLowerCase();
    }

    public String getName() {
      return name;
    }

    public Long getTotalCountSearchResults() {
      return totalCountSearchResults;
    }

    public HashSet<SearchResult> getTopSearchResults() {
      return topSearchResults;
    }

    /** This function fetches the total number of Bing search results and updates the "totalCountSearchResult"
     *  instance variable. Existing value is overridden.
     * @throws IOException
     */
    public void updateTotalCountSearchResults() throws IOException {
      LOGGER.debug("Updating totalCountSearchResults for name: " + name + ".");
      final String queryTerm = URLEncoder.encode(name, Charset.defaultCharset().name());
      final int top = 1;
      final int skip = 0;
      JSONObject results = fetchBingSearchAPIResponse(queryTerm, top, skip);
      String WebTotal = results.getString("WebTotal");
      totalCountSearchResults = Long.parseLong(WebTotal);
    }


    /** This function fetches the topN Bing search results for the current instance of NameSearchResult object
     * and updates the "topSearchResults" instance variable. Existing value is overridden.
     * @param topN Integer, number of Web results to fetch from Bing Search API
     * @throws IOException
     */
    public void updateTopSearchResults(Integer topN) throws IOException {
      LOGGER.debug("Updating topSearchResults for name: " + name + ".");
      topSearchResults = new HashSet<>();
      final String queryTerm = URLEncoder.encode(name, Charset.defaultCharset().name());
      // The Bing search API cannot return more than 100 results at once, but it is possible to iterate
      // through the results.
      // For example, if we need topN = 230 results, we will issue the following queries
      // (top and skip are URL parameters)
      // QUERY 1: top = 100, skip = 0
      // QUERY 2: top = 100, skip = 100
      // QUERY 3: top = 30, skip = 200
      Integer iterations = topN / MAX_RESULTS_PER_QUERY;
      Integer remainder = topN % MAX_RESULTS_PER_QUERY;
      for (int i = 0; i < iterations; i++) {
        topSearchResults.addAll(fetchSearchResults(queryTerm, MAX_RESULTS_PER_QUERY, MAX_RESULTS_PER_QUERY * i));
      }
      topSearchResults.addAll(fetchSearchResults(queryTerm, remainder, 100 * iterations));
    }
  }

  /** This function issues a Bing Search API call and parses the response to extract a set of SearchResults.
   * @param query (String) the term to query for.
   * @param top (int) URL parameter indicating how many results to return. Max value is 100.
   * @param skip (int) URL parameter indicating the offset for results. Has to comply with top + skip <= 1000.
   * @return returns a set of SearchResults containing the relevant information
   * @throws IOException
   */
  private HashSet<SearchResult> fetchSearchResults(String query, int top, int skip) throws IOException {
    HashSet<SearchResult> searchResults = new HashSet<>();
    JSONObject results = fetchBingSearchAPIResponse(query, top, skip);
    final JSONArray webResults = results.getJSONArray("Web");
    final int resultsLength = webResults.length();
    for (int i = 0; i < resultsLength; i++) {
      SearchResult searchResult = new SearchResult();
      final JSONObject aResult = webResults.getJSONObject(i);
      searchResult.populateFromJSONObject(aResult);
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
  public JSONObject fetchBingSearchAPIResponse(String queryTerm, int top, int skip) throws IOException {
    final String bingUrl = String.format(BING_URL_PATTERN, queryTerm, top, skip);
    JSONObject results;
    final URL url = new URL(bingUrl);
    URLConnection connection = url.openConnection();
    connection.setRequestProperty("Authorization", "Basic " + ACCOUNT_KEY_ENC);
    try (final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
      String inputLine;
      final StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      final JSONObject json = new JSONObject(response.toString());
      JSONObject d = json.getJSONObject("d");
      results = d.getJSONArray("results").getJSONObject(0);
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
  public HashSet<SearchResult> getAndCacheTopSearchResults(String name) throws IOException {

    String formattedName = name.toLowerCase();
    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject allFields = new BasicDBObject();
    whereQuery.put("name", formattedName);
    DBCursor cursor = bingCacheCollection.find(whereQuery, allFields);

    HashSet<SearchResult> searchResults = new HashSet<>();

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the topSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the topSearchResults are not populated.
    //    This case occurs when only totalCountSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the results
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the results.

    if (cursor.hasNext()) {
      // There is an existing entry in the DB
      BasicDBObject nameSearchResultDBObject = (BasicDBObject) cursor.next();
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
        nameSearchResults.updateTopSearchResults(TOP_N);
        // Get the top search results set
        HashSet<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
        // Transform Set into BasicDBList for storage
        topSearchResultsList = new BasicDBList();
        for (SearchResult topSearchResult : topSearchResults) {
          topSearchResultsList.add(topSearchResult.getBasicDBObject());
        }
        // Update the existing document in the cache
        BasicDBObject newTopSearchResults = new BasicDBObject();
        newTopSearchResults.append("$set", new BasicDBObject().append("topSearchResults", topSearchResultsList));
        bingCacheCollection.update(whereQuery, newTopSearchResults);
        return topSearchResults;
      }
    } else {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      // Query the results
      nameSearchResults.updateTopSearchResults(TOP_N);
      // Get the top search results set
      HashSet<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
      // Transform Set into BasicDBList for storage
      BasicDBList topSearchResultsList = new BasicDBList();
      for (SearchResult topSearchResult : topSearchResults) {
        topSearchResultsList.add(topSearchResult.getBasicDBObject());
      }
      // Save new document in the cache
      BasicDBObject newTopSearchResults = new BasicDBObject();
      newTopSearchResults.put("name", formattedName);
      newTopSearchResults.put("topSearchResults", topSearchResultsList);
      bingCacheCollection.save(newTopSearchResults);
      return topSearchResults;
    }
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
    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject allFields = new BasicDBObject();
    whereQuery.put("name", formattedName);
    DBCursor cursor = bingCacheCollection.find(whereQuery, allFields);

    Long totalCountSearchResults;

    // There are 3 cases:
    // 1) There is a corresponding entry in the cache AND the totalCountSearchResults are populated.
    //    In this case, we read from the cache and return the results.
    // 2) There is a corresponding entry in the cache BUT the totalCountSearchResults are not populated.
    //    This case occurs when only topSearchResults is populated.
    //    In this case, perform the relevant query, update the cache and return the total count
    // 3) There is no corresponding entry in the cache.
    //    In this case, perform the relevant query, create a new entry in the cache and return the total count.

    if (cursor.hasNext()) {
      // There is an existing entry in the cache
      BasicDBObject nameSearchResultDBObject = (BasicDBObject) cursor.next();
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
        nameSearchResults.updateTotalCountSearchResults();
        // Get the total count
        totalCountSearchResults = nameSearchResults.getTotalCountSearchResults();
        // Update the existing document in the cache
        BasicDBObject newTotalCountSearchResults = new BasicDBObject();
        newTotalCountSearchResults.append("$set", new BasicDBObject().append("totalCountSearchResults", totalCountSearchResults));
        bingCacheCollection.update(whereQuery, newTotalCountSearchResults );
        return totalCountSearchResults;
      }
    } else {
      // Case 3)
      LOGGER.debug("No corresponding entry in the cache. Fetching results and populating cache.");
      NameSearchResults nameSearchResults = new NameSearchResults(formattedName);
      // Query the results
      nameSearchResults.updateTotalCountSearchResults();
      // Get the total count
      totalCountSearchResults = nameSearchResults.getTotalCountSearchResults();
      // Save new document in the cache
      BasicDBObject newTotalCountSearchResults  = new BasicDBObject();
      newTotalCountSearchResults.put("name", formattedName);
      newTotalCountSearchResults.put("totalCountSearchResults", totalCountSearchResults);
      bingCacheCollection.save(newTotalCountSearchResults);
      return totalCountSearchResults;
    }
  }
}