package com.act.lcms.v3;



import chemaxon.formats.MolFormatException;
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


public class FormulaCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FormulaCorpus.class);
  private List<String> corpus = new ArrayList<>();
  private LargeMassToMoleculeMap massToMoleculeMap = new LargeMassToMoleculeMap();

  public FormulaCorpus() {
  }

  public FormulaCorpus(Collection<String> formulaList) {
    corpus = new ArrayList<>(formulaList);
  }

  public void populateMassToMoleculeMap() throws MolFormatException {
    Double mass;
    for (String formula : corpus) {
      //mass = Helpers.computeMassFromAtomicFormula(MassToFormula.getFormulaMap(formula)).rounded(6);
      //massToMoleculeMap.put(mass.floatValue(), formula);
    }
  }

  public LargeMassToMoleculeMap getMassToMoleculeMap() {
    return massToMoleculeMap;
  }

  /**
   * Write formula list to file.
   */
  public void writeToFile(File formulasFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter((new FileWriter(formulasFile)))) {
      for (String formula : getFormulaList()) {
        writer.write(formula);
        writer.newLine();
      }
    }
  }

  /**
   * Add the chemicals in the formulas file to the corpus.
   */
  public void loadCorpus(File formulasFile) throws IOException {

    try (BufferedReader formulaReader = getFormulaReader(formulasFile)) {

      String formula;
      while ((formula = formulaReader.readLine()) != null) {

        String trimmed = formula.trim();
        if (!trimmed.equals(formula)) {
          LOGGER.warn("Leading or trailing whitespace found in formula file.");
        }
        if (trimmed.equals("")) {
          LOGGER.warn("Blank line detected in formulas file and ignored.");
          continue;
        }
        corpus.add(formula);
      }
    }
  }

  /**
   * @return A reader for the list of formulas.
   */
  private BufferedReader getFormulaReader(File formulaFile) throws FileNotFoundException {
    FileInputStream formulaInputStream = new FileInputStream(formulaFile);
    return new BufferedReader(new InputStreamReader(formulaInputStream));
  }

  public List<String> getFormulaList() {
    return corpus;
  }
}
