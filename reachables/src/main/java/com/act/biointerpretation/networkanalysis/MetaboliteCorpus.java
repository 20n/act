package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class MetaboliteCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MetaboliteCorpus.class);
  private static final String DEFAULT_FILE_LOCATION = "/Volumes/shared-data/Michael/LowThresholdForRegression/dl.toIonMatchesFormulasNoMinusCholesterolMarkList.json.txt";
  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private List<Metabolite> corpus;

  MetaboliteCorpus(){}

  public JsonNode getJsonNodeFromFile(File inputFile) throws IOException {
    String fileString = new String(Files.readAllBytes(inputFile.toPath()));
    return MetaboliteCorpus.OBJECT_MAPPER.readTree(fileString);
  }

  public void populateFromJsonNode(JsonNode node) {
    JsonNode peaksNode = node.get("peaks");
    int size = peaksNode.size();
    LOGGER.info(size);
    Iterator<JsonNode> peaks = peaksNode.elements();
    // "moleculeMass": 516.3827,
    //    "rt": 209.61999893188477,
    //    "raw_mz": 517.39,
    //    "mz_band": 0.02,
     //   "mz": 517.39,

    List<Metabolite> l = new ArrayList<>(peaksNode.size());

    while (peaks.hasNext()) {
    }
  }


  public static void main(String[] args) throws IOException {
    MetaboliteCorpus metaboliteCorpus = new MetaboliteCorpus();
    File inputFile = new File(DEFAULT_FILE_LOCATION);
    JsonNode node = metaboliteCorpus.getJsonNodeFromFile(inputFile);
    metaboliteCorpus.populateFromJsonNode(node);
  }
}
