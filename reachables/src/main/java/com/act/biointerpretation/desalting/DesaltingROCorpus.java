package com.act.biointerpretation.desalting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class DesaltingROCorpus {

  private static final String DESALTER_CONSTANTS_FILE_PATH = "desalter_constants.txt";
  private static final String DESALTING_ROS_FILE_PATH = "desalting_ros.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("title")
  private String title;

  @JsonProperty("ros")
  private List<DesaltingRO> ros;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<DesaltingRO> getRos() {
    return ros;
  }

  public void setRos(List<DesaltingRO> ros) {
    this.ros = ros;
  }

  /**
   * This function returns a corpus Desalting ROs
   * @return DesaltingROCorpus object
   * @throws IOException
   */
  public DesaltingROCorpus getDesaltingROS() throws IOException {
    InputStream desaltingROSStream = INSTANCE_CLASS_LOADER.getResourceAsStream(DESALTING_ROS_FILE_PATH);
    DesaltingROCorpus corpus = OBJECT_MAPPER.readValue(desaltingROSStream, DesaltingROCorpus.class);
    return corpus;
  }

  /**
   * This function returns a reader file handle to the list of ROs files
   * @return BufferedReader reader
   * @throws FileNotFoundException
   */
  public BufferedReader getDesalterConstantsReader() throws FileNotFoundException {
    InputStream desalterConstantsStream = INSTANCE_CLASS_LOADER.getResourceAsStream(DESALTER_CONSTANTS_FILE_PATH);
    BufferedReader desaltConstantsReader = new BufferedReader(new InputStreamReader(desalterConstantsStream));
    return desaltConstantsReader;
  }
}
