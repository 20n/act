package com.act.analysis.synonyms;

import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemSynonymType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class holding synonyms for a chemical
 */


@JsonPropertyOrder({"inchi", "pubchem-synonyms", "mesh-terms", "chemaxon-traditional-name", "chemaxon-common-names"})
public class Synonyms {
  public Synonyms(String inchi) {
    this.inchi = inchi;
    this.brendaSynonyms = new ArrayList<>();
    this.pubchemSynonyms = new HashMap<>();
    this.meshTerms = new HashMap<>();
    this.chemaxonTraditionalName = null;
    this.chemaxonCommonNames = new ArrayList<>();
  }

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("brenda-synonyms")
  private List<String> brendaSynonyms;

  @JsonProperty("pubchem-synonyms")
  private Map<PubchemSynonymType, List<String>> pubchemSynonyms;

  @JsonProperty("mesh-terms")
  private Map<MeshTermType, List<String>> meshTerms;

  @JsonProperty("chemaxon-traditional-name")
  private String chemaxonTraditionalName;

  @JsonProperty("chemaxon-common-names")
  // https://docs.chemaxon.com/display/docs/Name+import+and+export+options
  private List<String> chemaxonCommonNames;

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  @JsonIgnore
  public Map<PubchemSynonymType, List<String>> getPubchemSynonyms() {
    return pubchemSynonyms;
  }

  @JsonIgnore
  public Map<MeshTermType, List<String>> getMeshTerms() {
    return meshTerms;
  }

  @JsonIgnore
  public String getChemaxonTraditionalName() {
    return chemaxonTraditionalName;
  }

  @JsonIgnore
  public List<String> getChemaxonCommonNames() {
    return chemaxonCommonNames;
  }

  @JsonIgnore
  public void setPubchemSynonyms(Map<PubchemSynonymType, List<String>> pubchemSynonyms) {
    this.pubchemSynonyms = pubchemSynonyms;
  }

  @JsonIgnore
  public void setMeshTerms(Map<MeshTermType, List<String>> meshTerms) {
    this.meshTerms = meshTerms;
  }

  @JsonIgnore
  public void setChemaxonTraditionalName(String chemaxonTraditionalName) {
    this.chemaxonTraditionalName = chemaxonTraditionalName;
  }

  @JsonIgnore
  public void setChemaxonCommonNames(List<String> chemaxonCommonNames) {
    this.chemaxonCommonNames = chemaxonCommonNames;
  }

  public List<String> getBrendaSynonyms() {
    return brendaSynonyms;
  }

  public void setBrendaSynonyms(List<String> brendaSynonyms) {
    this.brendaSynonyms = brendaSynonyms;
  }
}
