package com.act.biointerpretation.mechanisminspection;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
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

  public void setRos(List<Ero> eros) {
    this.ros = eros;
  }

  public ErosCorpus() {}

  /**
   * Loads entire RO corpus from file.
   * @throws IOException
   */
  public void loadCorpus() throws IOException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(EROS_FILE_PATH).getFile());
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosFile, ErosCorpus.class);
    setRos(erosCorpus.getRos());
  }

  /**
   * Builds an RO list from only the Ros specified in the given file.
   * @param fileName One RO ID per line.
   * @return Relevant Eros from the corpus.
   */
  public List<Ero> getRoListFromFile(String fileName) throws IOException {
    Set<Integer> roSet = new HashSet<>();

    BufferedReader eroReader = getErosReader(fileName);

    while(eroReader.ready()){
      roSet.add(Integer.parseInt(eroReader.readLine()));
    }

    eroReader.close();
    return getRoList(roSet);
  }

  /**
   * Builds an RO list from only the specified RO ids.
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

  /**
   * @param eroFileName a file containing the RO ids, with one RO ID per line.
   * @return A reader for the list of RO Ids.
   */
  private BufferedReader getErosReader(String eroFileName) throws FileNotFoundException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(eroFileName).getFile());
    FileInputStream erosInputStream = new FileInputStream(erosFile);
    BufferedReader erosReader = new BufferedReader(new InputStreamReader(erosInputStream));
    return erosReader;
  }
}
