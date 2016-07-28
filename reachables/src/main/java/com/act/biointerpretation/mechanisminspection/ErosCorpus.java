package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.impl.piccolo.io.FileFormatException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ErosCorpus implements Iterable<Ero> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ErosCorpus.class);
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String VALIDATION_EROS_FILE_NAME = "validation_eros.json";

  private List<Ero> ros;
  private Map<Integer, Ero> roIdToEroMap;

  public List<Ero> getRos() {
    return ros;
  }

  public void setRos(List<Ero> eros) {
    this.ros = eros;
  }

  /**
   * Loads the mechanistic validation RO corpus from the resources directory.
   *
   * @throws IOException
   */
  public void loadValidationCorpus() throws IOException {
    InputStream erosStream = INSTANCE_CLASS_LOADER.getResourceAsStream(VALIDATION_EROS_FILE_NAME);
    loadCorpus(erosStream);
  }

  /**
   * Loads an RO corpus from the supplied input stream.
   *
   * @param erosStream The input stream to load from.
   * @throws IOException
   */
  public void loadCorpus(InputStream erosStream) throws IOException {
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosStream, ErosCorpus.class);
    setRos(erosCorpus.getRos());
    buildRoIdToReactorMap();
  }

  /**
   * Get ros from corpus that have ids in input list.
   *
   * @param roIdList The list of relevant ids.
   * @return The list of relevant ros.
   */
  public List<Ero> getRos(List<Integer> roIdList) {
    Set<Integer> roSet = new HashSet<>(roIdList);

    List<Ero> result = new ArrayList<>();

    for (Ero ro : getRos()) {
      if (roSet.contains(ro.getId())) {
        result.add(ro);
      }
    }

    return result;
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

  /**
   * Gets the ERO with the given roId from the corpus.
   *
   * @param roId The ro id.
   * @return The Ero.
   */
  public Ero getEro(Integer roId) {
    return roIdToEroMap.get(roId);
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

  @Override
  public Iterator<Ero> iterator() {
    return getRos().iterator();
  }

  private void buildRoIdToReactorMap() throws FileFormatException {
    roIdToEroMap = new HashMap<>();
    for (Ero ro : ros) {
      if (roIdToEroMap.containsKey(ro.getId())) {
        throw new FileFormatException("RO corpus contains two ROs with same Id.");
      }
      roIdToEroMap.put(ro.getId(), ro);
    }
  }
}
