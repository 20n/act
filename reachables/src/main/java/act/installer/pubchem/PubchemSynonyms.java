package act.installer.pubchem;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PubchemSynonyms implements Serializable {
  private static final long serialVersionUID = 2111293889592103961L;

  @JsonProperty("pubchem_compound_id")
  String pubchemId;

  @JsonProperty("synonyms")
  @JsonSerialize(contentUsing = SortingSetSerializer.class)
  Map<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>> synonyms = new HashMap<>();

  @JsonProperty("MeSH_ids")
  @JsonSerialize(using = SortingSetSerializer.class)
  Set<String> meshIds = new HashSet<>();

  public PubchemSynonyms(String pubchemId) {
    this.pubchemId = pubchemId;
  }

  public PubchemSynonyms(String pubchemId,
                         Map<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>> synonyms,
                         Collection<String> meshIds) {
    this.pubchemId = pubchemId;
    this.synonyms.putAll(synonyms);
    this.meshIds.addAll(meshIds);
  }

  protected void addSynonym(PubchemTTLMerger.PC_SYNONYM_TYPES type, String synonym) {
    Set<String> existingVals = this.synonyms.get(type);
    if (existingVals == null) {
      existingVals = new HashSet<>();
      this.synonyms.put(type, existingVals);
    }
    existingVals.add(synonym);
  }

  protected void addSynonyms(PubchemTTLMerger.PC_SYNONYM_TYPES type, Set<String> synonyms) {
    Set<String> existingVals = this.synonyms.get(type);
    if (existingVals == null) {
      existingVals = new HashSet<>();
      this.synonyms.put(type, existingVals);
    }
    existingVals.addAll(synonyms);
  }

  public Map<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>> getSynonyms() {
    return synonyms;
  }

  protected void addMeSHId(String id) {
    this.meshIds.add(id);
  }

  protected void addMeSHIds(List<String> ids) {
    this.meshIds.addAll(ids);
  }

  @JsonGetter("MeSH_ids")
  public Set<String> getMeSHIds() {
    return meshIds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PubchemSynonyms that = (PubchemSynonyms) o;

    if (!pubchemId.equals(that.pubchemId)) return false;
    if (!synonyms.equals(that.synonyms)) return false;
    return meshIds.equals(that.meshIds);

  }

  @Override
  public int hashCode() {
    int result = pubchemId.hashCode();
    result = 31 * result + synonyms.hashCode();
    result = 31 * result + meshIds.hashCode();
    return result;
  }

  static class SortingSetSerializer extends JsonSerializer<Set<String>> {
    @Override
    public void serialize(Set<String> value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException, JsonProcessingException {
      List<String> valList = new ArrayList<>(value);
      Collections.sort(valList);
      gen.writeObject(valList);
    }
  }
}
