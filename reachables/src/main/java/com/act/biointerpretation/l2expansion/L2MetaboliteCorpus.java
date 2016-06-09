package com.act.biointerpretation.l2expansion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the set of metabolites to be used as a starting point for L2 expansion
 */
public class L2MetaboliteCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2MetaboliteCorpus.class);

  private String metabolitesFilePath;

  private List<String> corpus = new ArrayList<>();

  public L2MetaboliteCorpus(String metabolitesFilePath) {
    this.metabolitesFilePath = metabolitesFilePath;
  }

  /**
   * Add the chemicals in the metabolites file to the corpus as inchis.
   */
  public void loadCorpus() throws IOException {

    try (BufferedReader metaboliteReader = getMetabolitesReader()) {

      String inchi;
      while ((inchi = metaboliteReader.readLine()) != null) {

        String trimmed = inchi.trim();
        if (!trimmed.equals(inchi)) {
          LOGGER.warn("Leading or trailing whitespace found in metabolites file.");
        }
        if (trimmed.equals("")) {
          LOGGER.warn("Blank line detected in metabolites file and ignored.");
          continue;
        }
        corpus.add(inchi);
      }
    }
  }

  /**
   * @return A reader for the list of metabolites.
   */
  private BufferedReader getMetabolitesReader() throws FileNotFoundException {
    File metabolitesFile = new File(metabolitesFilePath);
    FileInputStream metabolitesInputStream = new FileInputStream(metabolitesFile);
    BufferedReader metabolitesReader = new BufferedReader(new InputStreamReader(metabolitesInputStream));
    return metabolitesReader;
  }

  public List<String> getMetaboliteList() {
    return corpus;
  }
}
