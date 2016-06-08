package act.server;

import act.installer.bing.NameSearchResults;
import act.installer.bing.SearchResult;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

import java.net.UnknownHostException;
import java.util.HashSet;

public class BingCacheMongoDB {
  private DBCollection dbBingCache; // the Bing Search collection is located in a separate database
  private String hostname;
  private int port;
  private String database;
  private DB mongoDB;

  public BingCacheMongoDB(String hostname, int port, String database) {
    this.hostname = hostname;
    this.port = port;
    this.database = database;
    init();
  }

  public void init() {
    try {
      MongoClient mongo = new MongoClient(hostname, port);
      mongoDB = mongo.getDB(database);

    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid host for Mongo Bing Cache server.");
    } catch (MongoException e) {
      throw new IllegalArgumentException("Could not initialize Mongo driver.");
    }
    this.dbBingCache = mongoDB.getCollection("cache");
  }

  public BasicDBObject getNameSearchResultDBObjectFromName(String formattedName) {
    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject allFields = new BasicDBObject();
    whereQuery.put("name", formattedName);
    return (BasicDBObject) dbBingCache.findOne(whereQuery, allFields);
  }

  public void cacheNameSearchResult(NameSearchResults nameSearchResults) {
    BasicDBObject nameSearchResultDBObject  = new BasicDBObject();
    nameSearchResultDBObject.put("name", nameSearchResults.getName());
    Long totalCountSearchResults = nameSearchResults.getTotalCountSearchResults();
    if (totalCountSearchResults >= 0) {
      nameSearchResultDBObject.put("totalCountSearchResults", totalCountSearchResults);
    }
    HashSet<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
    if (topSearchResults != null) {
      BasicDBList topSearchResultsList = new BasicDBList();
      for (SearchResult topSearchResult : topSearchResults) {
        topSearchResultsList.add(topSearchResult.getBasicDBObject());
      }
      nameSearchResultDBObject.put("topSearchResults", topSearchResultsList);
    }
    dbBingCache.save(nameSearchResultDBObject);
  }

  public void updateTotalCountSearchResults(String formattedName, NameSearchResults nameSearchResults) {
    BasicDBObject whereQuery = new BasicDBObject();
    whereQuery.put("name", formattedName);
    // Update the existing document in the cache
    BasicDBObject newTotalCountSearchResults = new BasicDBObject();
    newTotalCountSearchResults.append("$set",
        new BasicDBObject().append("totalCountSearchResults", nameSearchResults.getTotalCountSearchResults()));
    dbBingCache.update(whereQuery, newTotalCountSearchResults);
  }

  public void updateTopSearchResults(String formattedName, NameSearchResults nameSearchResults) {
    BasicDBObject whereQuery = new BasicDBObject();
    whereQuery.put("name", formattedName);
    // Transform Set into BasicDBList for storage
    BasicDBList topSearchResultsList = new BasicDBList();
    for (SearchResult topSearchResult : nameSearchResults.getTopSearchResults()) {
      topSearchResultsList.add(topSearchResult.getBasicDBObject());
    }
    // Update the existing document in the cache
    BasicDBObject newTopSearchResults = new BasicDBObject();
    newTopSearchResults.append("$set", new BasicDBObject().append("topSearchResults", topSearchResultsList));
    dbBingCache.update(whereQuery, newTopSearchResults);
  }
}
