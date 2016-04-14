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
  private Set<String> inchiSet = new HashSet<>();
  private Map<String, String> inchiToName = new HashMap<>();

  @JsonProperty("cofactors")
  private List<Cofactor> cofactors;

  public List<Cofactor> getCofactors() {
    return cofactors;
  }

  public void setCofactors(List<Cofactor> cofactors) {
    this.cofactors = cofactors;
  }

  public CofactorsCorpus() throws IOException {
    File cofactorsFile = new File(INSTANCE_CLASS_LOADER.getResource(COFACTORS_FILE_PATH).getFile());
    CofactorsCorpus corpus = OBJECT_MAPPER.readValue(cofactorsFile, CofactorsCorpus.class);

    List<Cofactor> cofactors = corpus.getCofactors();
    for (Cofactor cofactor : cofactors) {
      inchiSet.add(cofactor.getInchi());
      inchiToName.put(cofactor.getInchi(), cofactor.getName());
    }
  }

  public Set<String> getInchiSet() {
    return inchiSet;
  }

  public Map<String, String> getInchiToName() {
    return inchiToName;
  }
}
