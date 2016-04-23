package com.act.biointerpretation.step4_mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BlacklistedInchisCorpus {
  private static final String FILE_PATH = "blacklisted_inchis.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("blacklist_inchis")
  private List<BlacklistedInchi> blacklistedInchis;

  public List<BlacklistedInchi> getInchis() {
    return blacklistedInchis;
  }

  public void setInchis(List<BlacklistedInchi> inchis) {
    this.blacklistedInchis = inchis;
  }

  public BlacklistedInchisCorpus() {}

  public void loadCorpus() throws IOException {
    File file = new File(INSTANCE_CLASS_LOADER.getResource(FILE_PATH).getFile());
    BlacklistedInchisCorpus corpus = OBJECT_MAPPER.readValue(file, BlacklistedInchisCorpus.class);
    setInchis(corpus.getInchis());
  }

  public String renameInchiIfFoundInBlacklist(String inchi) {
    if (blacklistedInchis != null) {
      for (BlacklistedInchi blacklistedInchi : blacklistedInchis) {
        if (inchi.equals(blacklistedInchi.getWrong_inchi())) {
          return blacklistedInchi.getCorrect_inchi();
        }
      }
    }
    return inchi;
  }
}
