package com.act.lcms.v3;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
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

public class StructureCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(StructureCorpus.class);

  private List<String> corpus = new ArrayList<>();
  private LargeMassToMoleculeMap massToMoleculeMap = new LargeMassToMoleculeMap();

  public StructureCorpus() {
  }

  public StructureCorpus(Collection<String> structureList) {
    corpus = new ArrayList<>(structureList);
  }

  public void populateMassToMoleculeMap() throws MolFormatException {
    Double mass;
    NamedMolecule namedMolecule;
    for (String structure : corpus) {
      namedMolecule = null;
      mass = MolImporter.importMol(structure).getExactMass();
      namedMolecule.setMass(mass.floatValue());
      namedMolecule.setMolecule(structure);
      massToMoleculeMap.add(mass.floatValue(), namedMolecule);
    }
  }

  public LargeMassToMoleculeMap getMassToMoleculeMap() {
    return massToMoleculeMap;
  }

  public List<Molecule> getMolecules() {
    List<Molecule> results = new ArrayList<>(getStructureList().size());
    for (String structure : getStructureList()) {
      try {
        results.add(MoleculeImporter.importMolecule(structure));
      } catch (MolFormatException e) {
        LOGGER.error("MolFormatException on metabolite %s. %s", structure, e.getMessage());
      }
    }
    return results;
  }

  /**
   * Write structure list to file.
   */
  public void writeToFile(File structuresFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter((new FileWriter(structuresFile)))) {
      for (String structure : getStructureList()) {
        writer.write(structure);
        writer.newLine();
      }
    }
  }

  /**
   * Add the chemicals in the structures file to the corpus.
   */
  public void loadCorpus(File structuresFile) throws IOException {

    try (BufferedReader structureReader = getStructureReader(structuresFile)) {

      String structure;
      while ((structure = structureReader.readLine()) != null) {

        String trimmed = structure.trim();
        if (!trimmed.equals(structure)) {
          LOGGER.warn("Leading or trailing whitespace found in structure file.");
        }
        if (trimmed.equals("")) {
          LOGGER.warn("Blank line detected in structures file and ignored.");
          continue;
        }
        corpus.add(structure);
      }
    }
  }

  /**
   * @return A reader for the list of structures.
   */
  private BufferedReader getStructureReader(File structureFile) throws FileNotFoundException {
    FileInputStream structureInputStream = new FileInputStream(structureFile);
    return new BufferedReader(new InputStreamReader(structureInputStream));
  }

  public List<String> getStructureList() {
    return corpus;
  }


}
