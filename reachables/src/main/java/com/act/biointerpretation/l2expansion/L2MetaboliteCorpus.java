package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the set of metabolites to be used as a starting point for L2 expansion
 */
public class L2MetaboliteCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  private String metabolitesFilePath;
  private final Class INSTANCE_CLASS_LOADER = getClass();

  private Map<String, Molecule> corpus = new HashMap<String, Molecule>();

  public L2MetaboliteCorpus(String metaboltiesFilePath) {
    this.metabolitesFilePath = metaboltiesFilePath;
  }

  /**
   * Add the chemicals in the metabolites file to the corpus as chemaxon Molecules.
   */
  public void buildCorpus() throws IOException {
    BufferedReader metaboliteReader = getMetabolitesReader();

    while (metaboliteReader.ready()) {
      String inchi = metaboliteReader.readLine();
      try {
        corpus.put(inchi, MolImporter.importMol(inchi, "inchi"));
      } catch (MolFormatException e) {
        LOGGER.error("Cannot translate inchi to molecule:\n" + inchi);
      }
    }
  }

  /**
   * @return A reader for the list of metabolites.
   */
  private BufferedReader getMetabolitesReader() throws FileNotFoundException {
    File metabolitesFile = new File(INSTANCE_CLASS_LOADER.getResource(metabolitesFilePath).getFile());
    FileInputStream metabolitesInputStream = new FileInputStream(metabolitesFile);
    BufferedReader metabolitesReader = new BufferedReader(new InputStreamReader(metabolitesInputStream));
    return metabolitesReader;
  }

  public Map<String, Molecule> getCorpus() {
    return corpus;
  }
}
