package com.act.analysis.reactions;

import act.server.MongoDB;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class FindOrganismReactions {

  private static final String LOCAL_HOST = "localhost";
  private static final Integer PORT_NUMBER = 27017;

  private static final String HUMAN = "human";
  private static final String YEAST = "yeast";
  private static final String ORGANISM = HUMAN;

  private static Map<String, String> organismToRegex = new HashMap<>();
  static {
    organismToRegex.put("human","sapiens");
    organismToRegex.put("yeast", "Saccharomyces cerevisiae");
  };

  private static final File chemicalsFile = new File("/mnt/shared-data/Gil/networks", ORGANISM + ".chemicals");
  private static final File reactionsFile = new File("/mnt/shared-data/Gil/networks", ORGANISM + ".reactions");

  public static void main(String[] args) throws IOException {
    MongoDB mongoDB = new MongoDB(LOCAL_HOST, PORT_NUMBER, "marvin");

    OrganismSpecificReactions.findAllOrganismReactions(organismToRegex.get(ORGANISM), reactionsFile);

    try (BufferedReader reactionReader = new BufferedReader(new FileReader(reactionsFile))) {
      Set<String> inchis = new HashSet<>();
      String rxnId;
      while ((rxnId = reactionReader.readLine()) != null) {
        for (long chemicalId : mongoDB.getReactionFromUUID(Long.parseLong(rxnId)).getSubstrates()) {
          inchis.add(mongoDB.getChemicalFromChemicalUUID(chemicalId).getInChI());
        }

        for (long chemicalId : mongoDB.getReactionFromUUID(Long.parseLong(rxnId)).getProducts()) {
          inchis.add(mongoDB.getChemicalFromChemicalUUID(chemicalId).getInChI());
        }
      }

      new L2InchiCorpus(inchis).writeToFile(chemicalsFile);
    }

  }
}
