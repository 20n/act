package act.installer.bing;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

import java.util.HashSet;

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

  public void setName(String name) {
    this.name = name;
  }

  public void setTotalCountSearchResults(Long totalCountSearchResults) {
    this.totalCountSearchResults = totalCountSearchResults;
  }

  public void setTopSearchResults(HashSet<SearchResult> topSearchResults) {
    this.topSearchResults = topSearchResults;
  }

  public void saveNewResult(DBCollection collection) {
    BasicDBObject nameSearchResultDBObject  = new BasicDBObject();
    nameSearchResultDBObject.put("name", name);
    if (totalCountSearchResults >= 0) {
      nameSearchResultDBObject.put("totalCountSearchResults", totalCountSearchResults);
    }
    if (topSearchResults != null) {
      BasicDBList topSearchResultsList = new BasicDBList();
      for (SearchResult topSearchResult : topSearchResults) {
        topSearchResultsList.add(topSearchResult.getBasicDBObject());
      }
      nameSearchResultDBObject.put("topSearchResults", topSearchResultsList);
    }
    collection.save(nameSearchResultDBObject);
  }

  public void updateTotalCountSearchResults(DBCollection collection, BasicDBObject whereQuery) {
    // Update the existing document in the cache
    BasicDBObject newTotalCountSearchResults = new BasicDBObject();
    newTotalCountSearchResults.append("$set",
        new BasicDBObject().append("totalCountSearchResults", totalCountSearchResults));
    collection.update(whereQuery, newTotalCountSearchResults);
  }

  public void updateTopSearchResults(DBCollection collection, BasicDBObject whereQuery) {
    // Transform Set into BasicDBList for storage
    BasicDBList topSearchResultsList = new BasicDBList();
    for (SearchResult topSearchResult : topSearchResults) {
      topSearchResultsList.add(topSearchResult.getBasicDBObject());
    }
    // Update the existing document in the cache
    BasicDBObject newTopSearchResults = new BasicDBObject();
    newTopSearchResults.append("$set", new BasicDBObject().append("topSearchResults", topSearchResultsList));
    collection.update(whereQuery, newTopSearchResults);
  }
}
