package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemSynonymType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class SynonymData {
  @JsonProperty("pubchem")
  private Map<PubchemSynonymType, List<String>> pubchemSynonyms;
  @JsonProperty("mesh")
  private Map<MeshTermType, List<String>> meshHeadings;

  @JsonCreator
  public SynonymData(@JsonProperty("pubchem") Map<PubchemSynonymType, List<String>> pubchemSynonyms,
                  @JsonProperty("mesh") Map<MeshTermType, List<String>> meshHeadings) {
    this.pubchemSynonyms = pubchemSynonyms;
    this.meshHeadings = meshHeadings;
  }
}
