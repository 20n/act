package com.act.biointerpretation.step2_desalting;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class DesaltingROCorpus {

  private static final String DESALTER_CONSTANTS_FILE_PATH =
      "com/act/biointerpretation/step2_desalting/desalter_constants.txt";

  private static final String DESALTING_ROS_FILE_PATH =
      "com/act/biointerpretation/step2_desalting/desalting_ros.json";

  private final ClassLoader INSTANCE_CLASS_LOADER = getClass().getClassLoader();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private String title;
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

  public DesaltingROCorpus getDesaltingROS() throws IOException {
    File desaltingROSFile = new File(INSTANCE_CLASS_LOADER.getResource(DESALTING_ROS_FILE_PATH).getFile());
    DesaltingROCorpus corpus = OBJECT_MAPPER.readValue(desaltingROSFile, DesaltingROCorpus.class);
    return corpus;
  }

  public BufferedReader getDesalterConstantsReader() throws FileNotFoundException {
    File desalterConstantsFile = new File(INSTANCE_CLASS_LOADER.getResource(DESALTER_CONSTANTS_FILE_PATH).getFile());
    FileInputStream desalterConstantsInputStream = new FileInputStream(desalterConstantsFile);
    BufferedReader desaltConstantsReader = new BufferedReader(new InputStreamReader(desalterConstantsInputStream));
    return desaltConstantsReader;
  }
}
