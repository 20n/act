package com.act.biointerpretation.mechanisminspection;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ErosCorpus {
  private static final String EROS_FILE_PATH = "eros.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private List<Ero> ros;

  public List<Ero> getRos() {
    return ros;
  }

  /**
   * Builds an RO corpus from only the specified RO ids.
   * @param roSet the RO ID of every RO to be included in the corpus.
   * @return The list of Eros.
   */
  public List<Ero> getRoList(Set<Integer> roSet){
    List<Ero> corpus = new ArrayList<Ero>();

    for (Ero ero : getRos()) {
      if (roSet.contains(ero.getId())) {
        corpus.add(ero);
      }
    }

    return corpus;
  }

  public void setRos(List<Ero> eros) {
    this.ros = eros;
  }

  public ErosCorpus() {}

  public void loadCorpus() throws IOException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(EROS_FILE_PATH).getFile());
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosFile, ErosCorpus.class);
    setRos(erosCorpus.getRos());
  }
}
