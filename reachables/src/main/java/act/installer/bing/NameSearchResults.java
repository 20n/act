package act.installer.bing;

import java.util.Set;

public class NameSearchResults {
  private String name;
  private Long totalCountSearchResults = -1L;
  private Set<SearchResult> topSearchResults = null;

  public NameSearchResults(String name) {
    this.name = name.toLowerCase();
  }

  public String getName() {
    return name;
  }

  public Long getTotalCountSearchResults() {
    return totalCountSearchResults;
  }

  public Set<SearchResult> getTopSearchResults() {
    return topSearchResults;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTotalCountSearchResults(Long totalCountSearchResults) {
    this.totalCountSearchResults = totalCountSearchResults;
  }

  public void setTopSearchResults(Set<SearchResult> topSearchResults) {
    this.topSearchResults = topSearchResults;
  }
}
