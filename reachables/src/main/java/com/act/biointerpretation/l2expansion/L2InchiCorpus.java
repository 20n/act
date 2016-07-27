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
 * Represents a set of inchis.
 */
public class L2InchiCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2InchiCorpus.class);

  private List<String> corpus = new ArrayList<>();

  /**
   * Add the chemicals in the inchis file to the corpus.
   */
  public void loadCorpus(File inchisFile) throws IOException {

    try (BufferedReader inchiReader = getInchiReader(inchisFile)) {

      String inchi;
      while ((inchi = inchiReader.readLine()) != null) {

        String trimmed = inchi.trim();
        if (!trimmed.equals(inchi)) {
          LOGGER.warn("Leading or trailing whitespace found in inchi file.");
        }
        if (trimmed.equals("")) {
          LOGGER.warn("Blank line detected in inchis file and ignored.");
          continue;
        }
        corpus.add(inchi);
      }
    }
  }

  /**
   * @return A reader for the list of inchis.
   */
  private BufferedReader getInchiReader(File inchiFile) throws FileNotFoundException {
    FileInputStream inchiInputStream = new FileInputStream(inchiFile);
    return new BufferedReader(new InputStreamReader(inchiInputStream));
  }

  public List<String> getInchiList() {
    return corpus;
  }
}
