package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class SynonymData {

  @JsonCreator
  public SynonymData(@JsonProperty("pubchem") List<String> pubchemSynonyms,
                     @JsonProperty("mesh") List<String> meshSynonyms) {
    this.pubchemSynonyms = pubchemSynonyms;
    this.meshSynonyms = meshSynonyms;
  }

  @JsonProperty("pubchem")
  private List<String> pubchemSynonyms;

  @JsonProperty("mesh")
  private List<String> meshSynonyms;


}
