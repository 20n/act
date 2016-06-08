package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ErosCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ErosCorpus.class);

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
   * @return List of relevant Eros from the corpus.
   */
  public List<Ero> getRoListFromFile(String fileName) throws IOException {
    Set<Integer> roSet = new HashSet<>();

    BufferedReader eroReader = getErosReader(fileName);

    while(eroReader.ready()){
      String roId = eroReader.readLine();
      String trimmedId = roId.trim();

      if(!trimmedId.equals(roId)){
        LOGGER.warn("Leading or trailing whitespace found in ro id file.");
      }

      roSet.add(Integer.parseInt(trimmedId));
    }

    eroReader.close();
    return getRoList(roSet);
  }

  /**
   * Builds an RO list from only the specified RO IDs.
   * @param roSet The RO ID of every RO to be included in the corpus.
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
   * Gets a reader for the RO ID file.
   * @param eroFileName A file containing the RO ids, with one RO ID per line.
   * @return A reader for the list of RO Ids.
   */
  private BufferedReader getErosReader(String eroFileName) throws FileNotFoundException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(eroFileName).getFile());
    FileInputStream erosInputStream = new FileInputStream(erosFile);
    BufferedReader erosReader = new BufferedReader(new InputStreamReader(erosInputStream));
    return erosReader;
  }
}
