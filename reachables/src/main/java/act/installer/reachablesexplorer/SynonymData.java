package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemSynonymType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class SynonymData {
  @JsonProperty("pubchem")
  private Map<PubchemSynonymType, Set<String>> pubchemSynonyms;
  @JsonProperty("mesh")
  private Map<MeshTermType, Set<String>> meshHeadings;

  @JsonCreator
  public SynonymData(@JsonProperty("pubchem") Map<PubchemSynonymType, Set<String>> pubchemSynonyms,
                  @JsonProperty("mesh") Map<MeshTermType, Set<String>> meshHeadings) {
    this.pubchemSynonyms = pubchemSynonyms;
    this.meshHeadings = meshHeadings;
  }
}
