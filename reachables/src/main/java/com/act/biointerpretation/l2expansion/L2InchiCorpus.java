package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.lcms.MassCalculator;
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
import java.util.stream.Collectors;

/**
 * Represents a set of inchis.
 */
public class L2InchiCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2InchiCorpus.class);

  private static final String INCHI_IMPORT_SETTINGS = "inchi";

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
   * Write inchi list to file.
   */
  public void writeMasses(File inchisFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter((new FileWriter(inchisFile)))) {
      List<Double> massesChemaxon = new ArrayList<>();
      List<Double> exactMassesChemaxon = new ArrayList<>();

      for (String inchi : getInchiList()) {
        Molecule mol = MolImporter.importMol(inchi, "inchi");
        massesChemaxon.add(mol.getMass());
        exactMassesChemaxon.add(mol.getExactMass());
      }

      for (String inchi : getInchiList()) {
        Molecule mol = MolImporter.importMol(inchi, "inchi");
      }

      List<Double> massCalculatorMasses = getInchiList().stream().map(inchi -> MassCalculator.calculateMass(inchi)).collect(Collectors.toList());
      massCalculatorMasses.sort((a, b) -> Double.compare(a, b));

      massesChemaxon.sort((a, b) -> Double.compare(a, b));
      exactMassesChemaxon.sort((a, b) -> Double.compare(a, b));

      writer.write("chemaxon mass, chemaxon exact mass,massCalculator");
      for (int i = 0; i < massesChemaxon.size(); i++) {
        writer.write(Double.toString(massesChemaxon.get(i)));
        writer.write(",");
        writer.write(exactMassesChemaxon.get(i).toString());
        writer.write(",");
        writer.write(Double.toString(massCalculatorMasses.get(i)));
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

  /**
   * This function imports a given inchi to a Molecule.
   * TODO: Add a cache map from inchis -> molecules to this class to avoid redoing import
   *
   * @param inchi Input inchi.
   * @return The resulting Molecule.
   * @throws MolFormatException
   */
  public static Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }
}
