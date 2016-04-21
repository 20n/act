package com.act.biointerpretation.step4_mechanisminspection;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ErosCorpus {
  private static final String EROS_FILE_PATH = "eros.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private List<Eros> ros;

  public List<Eros> getRos() {
    return ros;
  }

  public void setRos(List<Eros> eros) {
    this.ros = eros;
  }

  public ErosCorpus() {}

  public void loadCorpus() throws IOException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(EROS_FILE_PATH).getFile());
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosFile, ErosCorpus.class);
    setRos(erosCorpus.getRos());
  }
}
