package com.act.biointerpretation.step3_cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FakeCofactorCorpus {
  private static final String FAKE_COFACTORS_FILE_PATH = "fake_cofactors.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private HashMap<String, String> fakeCofactorNameToRealCofactorName = new LinkedHashMap<>();

  @JsonProperty("fake_cofactors")
  private List<FakeCofactorMapping> fake_cofactors;

  public List<FakeCofactorMapping> getFake_cofactors() {
    return fake_cofactors;
  }

  public void setFake_cofactors(List<FakeCofactorMapping> cofactors) {
    this.fake_cofactors = cofactors;
  }

  public FakeCofactorCorpus() {}

  public void hydrateCorpus() throws IOException {
    File cofactorsFile = new File(INSTANCE_CLASS_LOADER.getResource(FAKE_COFACTORS_FILE_PATH).getFile());
    FakeCofactorCorpus corpus = OBJECT_MAPPER.readValue(cofactorsFile, FakeCofactorCorpus.class);

    List<FakeCofactorMapping> cofactors = corpus.getFake_cofactors();

    Map<Integer, FakeCofactorMapping> rankToCofactor = new TreeMap<>();
    for (FakeCofactorMapping cofactor : cofactors) {
      rankToCofactor.put(cofactor.getRank(), cofactor);
    }

    for (Map.Entry<Integer, FakeCofactorMapping> entry : rankToCofactor.entrySet()) {
      fakeCofactorNameToRealCofactorName.put(entry.getValue().getFake_cofactor_name(),
          entry.getValue().getCofactor_name());
    }
  }

  public Map<String, String> getFakeCofactorNameToRealCofactorName() {
    return fakeCofactorNameToRealCofactorName;
  }
}
