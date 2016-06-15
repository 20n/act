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
import java.util.List;

public class ErosCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ErosCorpus.class);
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String EROS_FILE_PATH = "eros.json";

  private List<Ero> ros;

  public List<Ero> getRos() {
    return ros;
  }

  public void setRos(List<Ero> eros) {
    this.ros = eros;
  }

  /**
   * Loads entire RO corpus from file.
   *
   * @throws IOException
   */
  public void loadCorpus() throws IOException {
    File erosFile = new File(INSTANCE_CLASS_LOADER.getResource(EROS_FILE_PATH).getFile());
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosFile, ErosCorpus.class);
    setRos(erosCorpus.getRos());
  }

  /**
   * Builds an ro list from only the ros specified in the given file.
   *
   * @param file A file with one ro id per line.
   * @return List of relevant Eros from the corpus.
   */
  public List<Integer> getRoIdListFromFile(File file) throws IOException {
    List<Integer> roIdList = new ArrayList<>();

    try (BufferedReader eroReader = getErosReader(file)) {

      String roId;
      while ((roId = eroReader.readLine()) != null) {
        String trimmedId = roId.trim();

        if (!trimmedId.equals(roId)) {
          LOGGER.warn("Leading or trailing whitespace found in ro id file.");
        }
        if (trimmedId.equals("")) {
          LOGGER.warn("Blank line detected in ro id file and ignored.");
          continue;
        }

        roIdList.add(Integer.parseInt(trimmedId));
      }
    }

    return roIdList;
  }

  public List<Integer> getAllRoIds() {
    List<Integer> result = new ArrayList<>();
    for (Ero ro : this.getRos()) {
      result.add(ro.getId());
    }
    return result;
  }

  /**
   * Gets a reader for the RO ID file.
   *
   * @param eroFileName A file containing the RO ids, with one RO ID per line.
   * @return A reader for the list of RO Ids.
   */
  private BufferedReader getErosReader(String eroFileName) throws FileNotFoundException {
    return getErosReader(new File(eroFileName));
  }

  /**
   * Gets a reader for the RO ID file.
   *
   * @param erosFile A file containing the RO ids, with one RO ID per line.
   * @return A reader for the list of RO Ids.
   */
  private BufferedReader getErosReader(File erosFile) throws FileNotFoundException {
    FileInputStream erosInputStream = new FileInputStream(erosFile);
    BufferedReader erosReader = new BufferedReader(new InputStreamReader(erosInputStream));
    return erosReader;
  }
}
