package com.act.biointerpretation.networkanalysis;

import com.act.lcms.v2.DetectedPeak;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class MetaboliteCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MetaboliteCorpus.class);
  private static final String DEFAULT_FILE_LOCATION = "/Volumes/shared-data/Michael/LowThresholdForRegression/dl.toIonMatchesFormulasNoMinusCholesterolMarkList.json.txt";
  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private List<Metabolite> corpus;

  // Basic constructor
  MetaboliteCorpus(){
    corpus = new ArrayList<>();
  }

  // Return the corpus
  public List<Metabolite> getCorpus() {
    return corpus;
  }
  
  private JsonNode getJsonNodeFromFile(File inputFile) throws IOException {
    String fileString = new String(Files.readAllBytes(inputFile.toPath()));
    return MetaboliteCorpus.OBJECT_MAPPER.readTree(fileString);
  }

  private List<DetectedPeak> getDetectedPeaksFromJsonNode(JsonNode node) {
    JsonNode peaksNode = node.get("peaks");
    int size = peaksNode.size();
    LOGGER.info(size);
    Iterator<JsonNode> peaks = peaksNode.elements();

    JsonNode matchingInchisNode = node.get("matching_inchi_hashes");
    Iterator<JsonNode> matchingInchis = matchingInchisNode.elements();
    Map<Long, List<String>> m = new HashMap<>();
    while (matchingInchis.hasNext()) {
      JsonNode inchis = matchingInchis.next();
      Long code = inchis.get("code").asLong();
      List<String> vals = new ArrayList<>();
      Iterator<JsonNode> valsIte = inchis.get("vals").elements();
      while (valsIte.hasNext()) {
        vals.add(valsIte.next().get(0).toString());
      }

      m.put(code, vals);
    }

    List<DetectedPeak> detectedPeaks = new ArrayList<>();
    while (peaks.hasNext()) {
      DetectedPeak peak = new DetectedPeak();
      peak.parseFromJsonNode(peaks.next(), m);
      detectedPeaks.add(peak);
    }

    return detectedPeaks;
  }

  private void populateFromDetectedPeaks(List<DetectedPeak> detectedPeaks) throws JsonProcessingException {
    detectedPeaks.forEach(p -> corpus.addAll(p.getMetabolites()));
  }

  public void populateCorpusFromJsonFile(File inputFile) throws IOException {
    JsonNode node = getJsonNodeFromFile(inputFile);
    List<DetectedPeak> peaks = getDetectedPeaksFromJsonNode(node);
    populateFromDetectedPeaks(peaks);
  }

  public MetabolismNetwork getMetabolismNetwork() {
    MetabolismNetwork network = new MetabolismNetwork();
    corpus.forEach(metabolite -> network.addNode(metabolite.getMass(),metabolite.getInchi()));
    return network;
  }

  public static void main(String[] args) throws Exception {
    MetaboliteCorpus metaboliteCorpus = new MetaboliteCorpus();
    File inputFile = new File(DEFAULT_FILE_LOCATION);
    metaboliteCorpus.populateCorpusFromJsonFile(inputFile);
    System.out.println(OBJECT_MAPPER.writeValueAsString(metaboliteCorpus.getMetabolismNetwork()));
  }
}
