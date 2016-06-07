package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jdk.nashorn.internal.ir.debug.JSONWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class L2ExpansionDriver {

  private static final String METABOLITES_FILE = "PABA_metabolites.txt";
  private static final Set<Integer> RO_LIST = new HashSet<Integer>(Arrays.asList(358, 33, 75, 342, 357));
  private static final String OUTPUT_FILE_PATH =
          "./src/main/resources/com/act/biointerpretation/l2expansion/l2_predictions.txt";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void main(String[] args) throws IOException {

    //Initialize input corpuses and expander
    L2MetaboliteCorpus metaboliteCorpus = new L2MetaboliteCorpus(METABOLITES_FILE);
    L2RoCorpus roCorpus = new L2RoCorpus(RO_LIST);
    L2Expander expander = new L2Expander(roCorpus, metaboliteCorpus);

    // Carry out L2 expansion
    L2PredictionCorpus predictionCorpus = expander.getPredictionCorpus();

    // Print prediction corpus as json file
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    OBJECT_MAPPER.writeValue(predictionCorpus.getPredictionWriter(OUTPUT_FILE_PATH), predictionCorpus);
  }
}
