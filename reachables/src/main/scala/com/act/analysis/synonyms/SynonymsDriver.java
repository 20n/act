package com.act.analysis.synonyms;


import com.act.biointerpretation.l2expansion.L2InchiCorpus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SynonymsDriver {

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
    List<Synonyms> synonymses = inchis.stream().map(Synonyms::new).collect(Collectors.toList());
    synonymses.forEach(SynonymsExtractor::populateChemaxonSynonyms);
    synonymses.forEach(extractor::populateBrendaSynonyms);
    synonymses.forEach(extractor::populatePubchemSynonyms);

    try {
      new BufferedWriter(new FileWriter(OUTPUT_LOCATION)).write(SynonymsTSVWriter.toCSV(synonymses));
    } catch (IOException e) {

    }
  }

}
