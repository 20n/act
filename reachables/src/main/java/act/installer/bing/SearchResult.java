package act.installer.bing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;

/**
 * This class contains the structure of a single search result, as returned by the Bing Search API.
 * It also provides convenient translation methods from JsonNode and BasicDBObject and to BasicDBObject.
 */

public class SearchResult {

  // The ID field was removed with v5.0 of the API
  // Since we didn't use it much, we removed it from SearchResults as well.
  // See upgrade guide: https://msdn.microsoft.com/en-US/library/mt707570.aspx

  private String title;
  private String description;
  private String url;

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public void populateFromJsonNode(JsonNode result) {
    setDescription(result.path("snippet").textValue());
    setTitle(result.path("name").textValue());
    setUrl(result.path("displayUrl").textValue());
  }

  public void populateFromBasicDBObject(BasicDBObject result) {
    setDescription((String) result.get("description"));
    setTitle((String) result.get("title"));
    setUrl((String) result.get("url"));
  }

  public BasicDBObject getBasicDBObject() {
    BasicDBObject topSearchResultDBObject = new BasicDBObject();
    topSearchResultDBObject.put("title", getTitle());
    topSearchResultDBObject.put("description", getDescription());
    topSearchResultDBObject.put("url", getUrl());
    return topSearchResultDBObject;
  }
}
