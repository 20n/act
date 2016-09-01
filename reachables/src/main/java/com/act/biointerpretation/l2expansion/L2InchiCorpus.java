package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a set of inchis.
 */
public class L2InchiCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2InchiCorpus.class);

  private static final String INCHI_IMPORT_SETTINGS = "inchi";
  private static ConcurrentHashMap<String, Molecule> inchiMoleculeCache = new ConcurrentHashMap<>();
  private List<String> corpus = new ArrayList<>();

  public L2InchiCorpus() {
  }

  public L2InchiCorpus(Collection<String> inchiList) {
    corpus = new ArrayList<>(inchiList);
  }

  /**
   * This function imports a given inchi to a Molecule.
   *
   * @param inchi Input inchi.
   * @return The resulting Molecule.
   * @throws MolFormatException
   */
  static Molecule importMolecule(String inchi) throws MolFormatException {
    // We can't use the fancier methods here because MolImporter throws a checked exception and lambdas don't allow that
    Molecule inchiMolecule = inchiMoleculeCache.get(inchi);

    if (inchiMolecule == null) {
      inchiMolecule = MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
      inchiMoleculeCache.put(inchi, inchiMolecule);
    }

    return inchiMolecule;
  }

  /**
   * Wraps mass filtering so that it can be used as a step in a workflow
   *
   * @param inputSubstrates The initial list of substrates.
   * @param outputFile      The file to which to write the output.
   * @param massThreshold   The maximum mass to allow, in Daltons.
   * @return A JavaRunnable that can be used in a workflow.
   */
  public static JavaRunnable getRunnableSubstrateFilterer(File inputSubstrates,
                                                          File outputFile,
                                                          Integer massThreshold) {
    return new JavaRunnable() {

      @Override
      public void run() throws IOException {
        // Verify files
        FileChecker.verifyInputFile(inputSubstrates);
        FileChecker.verifyAndCreateOutputFile(outputFile);

        // Build input corpus
        L2InchiCorpus inchis = new L2InchiCorpus();
        inchis.loadCorpus(inputSubstrates);

        // Apply filter
        inchis.filterByMass(massThreshold);

        // Write to output file
        inchis.writeToFile(outputFile);
      }

      @Override
      public String toString() {
        return "mass_filterer_" + massThreshold.toString();
      }
    };
  }

  public void filterByMass(Integer massCutoff) {
    corpus.removeIf(
            inchi ->
            {
              try {
                Molecule mol = importMolecule(inchi);
                if (mol.getMass() > massCutoff) {
                  LOGGER.warn("Throwing out molecule %s because of mass %f and %d atoms.",
                          inchi, mol.getMass(), mol.getAtomCount());
                  return true;
                }
                return false;
              } catch (MolFormatException e) {
                LOGGER.error("MolFormatException on metabolite %s. %s", inchi, e.getMessage());
                return true;
              }
            }
    );
  }

  public List<Molecule> getMolecules() {
    List<Molecule> results = new ArrayList<>(getInchiList().size());
    for (String inchi : getInchiList()) {
      try {
        results.add(importMolecule(inchi));
      } catch (MolFormatException e) {
        LOGGER.error("MolFormatException on metabolite %s. %s", inchi, e.getMessage());
      }
    }
    return results;
  }

  /**
   * Write inchi list to file.
   */
  public void writeToFile(File inchisFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter((new FileWriter(inchisFile)))) {
      for (String inchi : getInchiList()) {
        writer.write(inchi);
        writer.newLine();
      }
    }
  }

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