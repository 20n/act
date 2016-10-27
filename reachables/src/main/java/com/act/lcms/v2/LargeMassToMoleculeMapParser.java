package com.act.lcms.v3;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class LargeMassToMoleculeMapParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LargeMassToMoleculeMapParser.class);

  private static final String DEFAULT_INPUT_FILE = "/Volumes/shared-data/Saurabh/formulae-enum/small-formulae-stable.tsv";
  private static final String DEFAULT_TEST_FILE = "/Volumes/shared-data/Thomas/test-small-formulae-stable.tsv";
  private static final String DEFAULT_NAMED_INCHIS = "/Volumes/shared-data/Thomas/L2inchis.named";

  private static final String DEFAULT_MOLECULE_HEADER = "inchi";
  private static final String DEFAULT_FORMULA_HEADER = "formula";
  private static final String DEFAULT_MASS_HEADER = "mass";

  private static final String TSV_SEPARATOR = "\t";

  private String moleculeHeader = null;
  private String massHeader = null;
  private String namesHeader = null;

  private LargeMassToMoleculeMap massToMoleculeMap = new LargeMassToMoleculeMap();

  public LargeMassToMoleculeMap getMassToMoleculeMap() {
    return massToMoleculeMap;
  }

  public LargeMassToMoleculeMapParser() {
    this.moleculeHeader = DEFAULT_MOLECULE_HEADER;
    this.massHeader = DEFAULT_MASS_HEADER;
  }

  public LargeMassToMoleculeMapParser(String moleculeHeader, String massHeader) {
    this.moleculeHeader = moleculeHeader;
    this.massHeader = massHeader;
  }

  public LargeMassToMoleculeMapParser(String moleculeHeader, String massHeader, String namesHeader) {
    this.moleculeHeader = moleculeHeader;
    this.massHeader = massHeader;
    this.namesHeader = namesHeader;
  }

  public void setNamesHeader(String namesHeader) {
    this.namesHeader = namesHeader;
  }

  public void parse(File inputFile) throws IOException {

    Float mass;
    String molecule;
    String line;
    String[] splitLine;
    String name;
    Integer nameIndex = -1;
    NamedMolecule namedMolecule;

    try (BufferedReader formulaeReader = getFormulaeReader(inputFile)) {

      String headerLine = formulaeReader.readLine();
      List<String> headers = Arrays.asList(headerLine.split(TSV_SEPARATOR));

      assert headers.contains(moleculeHeader);
      assert headers.contains(massHeader);
      Integer massIndex = headers.indexOf(massHeader);
      Integer moleculeIndex = headers.indexOf(moleculeHeader);

      if (namesHeader != null) {
        assert headers.contains(namesHeader);
        nameIndex = headers.indexOf(namesHeader);
      }

      int i = 0;
      while (formulaeReader.ready()) {

        namedMolecule = new NamedMolecule();

        if (i % 1000000 == 0) {
          LOGGER.info("Formulae processed so far: %d", i);
        }

        line = formulaeReader.readLine();
        splitLine = line.split(TSV_SEPARATOR);

        mass = Float.parseFloat(splitLine[massIndex]);
        namedMolecule.setMass(mass);
        molecule = splitLine[moleculeIndex];
        namedMolecule.setMolecule(molecule);

        if (namesHeader != null) {
          name = splitLine[nameIndex];
          namedMolecule.setName(name);
        }

        massToMoleculeMap.add(mass, namedMolecule);
        i++;
      }
    }
  }

  public void parseNamedInchis(File inputFile) throws IOException {

    Float mass;
    String molecule;
    String line;
    String[] splitLine;
    String name;
    Integer nameIndex = -1;
    NamedMolecule namedMolecule;
    Double doubleMass;

    try (BufferedReader formulaeReader = getFormulaeReader(inputFile)) {

      String headerLine = formulaeReader.readLine();
      List<String> headers = Arrays.asList(headerLine.split(TSV_SEPARATOR));

      assert headers.contains(moleculeHeader);
      Integer moleculeIndex = headers.indexOf(moleculeHeader);


      if (namesHeader != null) {
        assert headers.contains(namesHeader);
        nameIndex = headers.indexOf(namesHeader);
      }

      int i = 0;
      while (formulaeReader.ready()) {

        namedMolecule = new NamedMolecule();

        if (i % 1000000 == 0) {
          LOGGER.info("Structure processed so far: %d", i);
        }

        line = formulaeReader.readLine();
        splitLine = line.split(TSV_SEPARATOR);

        molecule = splitLine[moleculeIndex];
        namedMolecule.setMolecule(molecule);
        try {
          doubleMass = MolImporter.importMol(molecule).getExactMass();
          mass = doubleMass.floatValue();
          namedMolecule.setMass(mass);
        } catch (MolFormatException e) {
          LOGGER.error("Couldnot parse");
          continue;
        }

        if (namesHeader != null) {
          name = splitLine[nameIndex];
          namedMolecule.setName(name);
        }

        massToMoleculeMap.add(namedMolecule.getMass(), namedMolecule);
        i++;
      }
    }
  }

  private BufferedReader getFormulaeReader(File inchiFile) throws FileNotFoundException {
    FileInputStream formulaeInputStream = new FileInputStream(inchiFile);
    return new BufferedReader(new InputStreamReader(formulaeInputStream));
  }

  public static void main(String[] args) throws Exception {
    LargeMassToMoleculeMapParser parser = new LargeMassToMoleculeMapParser(DEFAULT_FORMULA_HEADER, DEFAULT_MASS_HEADER);
    parser.parse(new File(DEFAULT_TEST_FILE));
    System.out.println(parser.getMassToMoleculeMap());

    parser = new LargeMassToMoleculeMapParser();
    parser.setNamesHeader("name");
    parser.parseNamedInchis(new File(DEFAULT_NAMED_INCHIS));
    System.out.println(parser.getMassToMoleculeMap());
  }
}
