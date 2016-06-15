package act.installer.bing;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * This class represents a single usage term with the set of URLs it was found in
 * (more exactly in the associated description).
 * Provides methods for populating the object and translating it to BasicDBObject
 */

public class UsageTermUrlSet {
  private static final Logger LOGGER = LogManager.getFormatterLogger(UsageTermUrlSet.class);

  private String usageTerm;
  private Set<String> urlSet = new HashSet<>();

  public Set<String> getUrlSet() {
    return urlSet;
  }

  public UsageTermUrlSet(String usageTerm) {
    this.usageTerm = usageTerm;
  }

  public void populateUrlsFromNameSearchResults(NameSearchResults nameSearchResults) {
    Set<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
    if (topSearchResults == null) {
      LOGGER.debug("Top search results have not been initialized for %s", nameSearchResults.getName());
      return;
    }
    for (SearchResult searchResult : topSearchResults) {
      String description = searchResult.getDescription();
      if (description.toLowerCase().contains(usageTerm)) {
        urlSet.add(searchResult.getUrl());
      }
    }
  }

  public BasicDBObject getBasicDBObject() {
    BasicDBObject usageTermUrlSetBasicDBObject = new BasicDBObject("usage_term", usageTerm);
    BasicDBList urlsBasicDBList = new BasicDBList();
    for (String url : urlSet) {
      urlsBasicDBList.add(url);
    }
    usageTermUrlSetBasicDBObject.put("urls", urlsBasicDBList);
    return usageTermUrlSetBasicDBObject;
  }

}
