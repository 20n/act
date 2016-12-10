package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PatentSummary {
  private static final String BASE_URL_FORMAT = "http://patft1.uspto.gov/netacgi/nph-Parser?patentnumber=%s";

  @JsonProperty(value = "id", required = true)
  private String id;
  @JsonProperty(value = "title", required = true)
  private String title;
  @JsonProperty(value = "relevance_score", required = false)
  private Float relevanceScore;

  private PatentSummary() {

  }

  public PatentSummary(String id, String title, Float relevanceScore) {
    this.id = id;
    this.title = title;
    this.relevanceScore = relevanceScore;
  }

  public String getId() {
    return id;
  }

  private void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  private void setTitle(String title) {
    this.title = title;
  }

  public Float getRelevanceScore() {
    return relevanceScore;
  }

  private void setRelevanceScore(Float relevanceScore) {
    this.relevanceScore = relevanceScore;
  }

  public String generateUSPTOURL() {
    // With help from http://patents.stackexchange.com/questions/3304/how-to-get-a-patent-or-patent-application-permalink-at-the-uspto-website

    // Strip country designator prefix and leading zeroes, then ditch the publication file suffix.
    String shortId = id.replaceFirst("^[A-Z]*0*", "").replaceFirst("-.*$", "");
    return String.format(BASE_URL_FORMAT, shortId);
  }
}
