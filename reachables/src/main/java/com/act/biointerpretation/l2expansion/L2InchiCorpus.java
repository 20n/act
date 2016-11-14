package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType;
import com.act.analysis.chemicals.molecules.MoleculeFormat$;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
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

/**
 * Represents a set of inchis.
 */
public class L2InchiCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2InchiCorpus.class);
  private List<String> corpus = new ArrayList<>();

  public L2InchiCorpus() {
  }

  public L2InchiCorpus(Collection<String> inchiList) {
    corpus = new ArrayList<>(inchiList);
  }

  public void filterByMass(Integer massCutoff) {
    corpus.removeIf(
            inchi ->
            {
              try {
                // Defaults to "inchi"
                Molecule mol = MoleculeImporter.importMolecule(inchi);
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
    List<MoleculeFormat.MoleculeFormatType> wrappedInchi = new ArrayList<>();
    wrappedInchi.add(MoleculeFormat.stdInchi$.MODULE$);
    return getMolecules(wrappedInchi);
  }

  public List<Molecule> getMolecules(List<MoleculeFormat.MoleculeFormatType> formats) {
    // We take in a string list here because java won't load in the scala enumeration type...
    List<MoleculeFormatType> formatList = new ArrayList<>();
    formatList.addAll(formats);

    List<Molecule> results = new ArrayList<>(getInchiList().size());
    for (String inchi : getInchiList()) {
      try {
        results.add(MoleculeImporter.importMolecule(inchi, formatList));
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

      String moleculeString;
      while ((moleculeString = inchiReader.readLine()) != null) {

        String trimmedMolecule = moleculeString.trim();
        if (!trimmedMolecule.equals(moleculeString)) {
          LOGGER.warn("Leading or trailing whitespace found in molecule string file.");
        }
        if (trimmedMolecule.equals("")) {
          LOGGER.warn("Blank line detected in molecule string file and ignored.");
          continue;
        }
        corpus.add(trimmedMolecule);
      }
    }
    LOGGER.info("Loaded " + corpus.size() + " molecules into corpus.");
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
}
