package act.installer.bing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;

public class SearchResult {
  private String id;
  private String title;
  private String description;
  private String url;

  public void setId(String id) {
    this.id = id;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getId() {
    return id;
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
    setDescription(result.path("Description").textValue());
    setId(result.path("ID").asText());
    setTitle(result.path("Title").textValue());
    setUrl(result.path("Url").textValue());
  }

  public void populateFromBasicDBObject(BasicDBObject result) {
    setDescription((String) result.get("description"));
    setId((String) result.get("id"));
    setTitle((String) result.get("title"));
    setUrl((String) result.get("url"));
  }

  public BasicDBObject getBasicDBObject() {
    BasicDBObject topSearchResultDBObject = new BasicDBObject();
    topSearchResultDBObject.put("id", getId());
    topSearchResultDBObject.put("title", getTitle());
    topSearchResultDBObject.put("description", getDescription());
    topSearchResultDBObject.put("url", getUrl());
    return topSearchResultDBObject;
  }
}
