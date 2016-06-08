package act.installer.bing;

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
}
