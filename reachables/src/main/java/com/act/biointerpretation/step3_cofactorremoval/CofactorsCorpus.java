package com.act.biointerpretation.step3_cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CofactorsCorpus {
  private static final String COFACTORS_FILE_PATH = "cofactors.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private Map<String, String> inchiToName = new HashMap<>();
  private Map<String, Integer> inchiToRank = new HashMap<>();

  @JsonProperty("cofactors")
  private List<Cofactor> cofactors;

  public List<Cofactor> getCofactors() {
    return cofactors;
  }

  public void setCofactors(List<Cofactor> cofactors) {
    this.cofactors = cofactors;
  }

  public CofactorsCorpus() {}

  public void loadCorpus() throws IOException {
    File cofactorsFile = new File(INSTANCE_CLASS_LOADER.getResource(COFACTORS_FILE_PATH).getFile());
    CofactorsCorpus corpus = OBJECT_MAPPER.readValue(cofactorsFile, CofactorsCorpus.class);

    List<Cofactor> cofactors = corpus.getCofactors();
    for (Cofactor cofactor : cofactors) {
      inchiToName.put(cofactor.getInchi(), cofactor.getName());
      inchiToRank.put(cofactor.getInchi(), cofactor.getRank());
    }
  }

  public Map<String, String> getInchiToName() {
    return inchiToName;
  }

  public Map<String, Integer> getInchiToRank() {
    return inchiToRank;
  }
}
