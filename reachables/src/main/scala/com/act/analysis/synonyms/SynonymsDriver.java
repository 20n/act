package com.act.analysis.synonyms;


import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SynonymsDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SynonymsDriver.class);

  private static final String LOCATION = "/mnt/shared-data/Thomas/L2inchis";
  private static final String OUTPUT_LOCATION = "/mnt/shared-data/Thomas/synonyms-test/L2inchis-synonyms";

  public static List<String> getInchis(File file) {
    L2InchiCorpus inchiCorpus = new L2InchiCorpus();
    try {
      inchiCorpus.loadCorpus(file);
    } catch (IOException e) {
      return Collections.emptyList();
    }
    return inchiCorpus.getInchiList();
  }

  public static void main(String[] args) {

    SynonymsExtractor extractor = new SynonymsExtractor();
    List<String> inchis = SynonymsDriver.getInchis(new File(LOCATION));
    inchis = inchis.subList(0,10);
    List<Synonyms> synonymses = inchis.stream().map(Synonyms::new).collect(Collectors.toList());
    LOGGER.info("Constructed list of %d synonymses", synonymses.size());
    synonymses.forEach(SynonymsExtractor::populateChemaxonSynonyms);
    LOGGER.info("successfully updated chemaxon synonyms");
    synonymses.forEach(extractor::populateBrendaSynonyms);
    LOGGER.info("successfully updated brenda synonyms");
    synonymses.forEach(extractor::populatePubchemSynonyms);
    LOGGER.info("successfully updated pubchem synonyms");

    String csvSynonyms;
    try {
      csvSynonyms = SynonymsTSVWriter.toCSV(synonymses);
      LOGGER.info(csvSynonyms);
      PrintWriter output = new PrintWriter(OUTPUT_LOCATION);
      output.print(csvSynonyms);
      output.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
