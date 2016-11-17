package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class WikipediaData {

  @JsonCreator
  public WikipediaData(@JsonProperty("url") String url, @JsonProperty("text") String text) {
    this.url = url;
    this.text = text;
  }

  @JsonProperty("url")
  private String url;

  @JsonProperty("text")
  private String text;

}
