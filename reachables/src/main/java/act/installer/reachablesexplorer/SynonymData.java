package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemSynonymType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
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

  public Map<PubchemSynonymType, Set<String>> getPubchemSynonyms() {
    return Collections.unmodifiableMap(this.pubchemSynonyms); // TODO: is unmodifiable worth the overhead?
  }

  public Set<String> getPubchemSynonymsByType(PubchemSynonymType type) {
    return this.pubchemSynonyms.containsKey(type) ?
        Collections.unmodifiableSet(this.pubchemSynonyms.get(type)) :
        Collections.emptySet();
  }

  public Map<MeshTermType, Set<String>> getMeshHeadings() {
    return Collections.unmodifiableMap(this.meshHeadings);
  }

  public Set<String> getMeshHeadingsByType(MeshTermType type) {
    return this.meshHeadings.containsKey(type) ?
        Collections.unmodifiableSet(this.meshHeadings.get(type)) :
        Collections.emptySet();
  }
}
