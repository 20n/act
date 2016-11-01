package com.act.lcms.v2;


import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import com.act.jobs.FileChecker;
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

public class MassToRawMetaboliteMapParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MassToRawMetaboliteMapParser.class);

  private static final String DEFAULT_STRUCTURE_HEADER = "inchi";
  private static final String DEFAULT_FORMULA_HEADER = "formula";
  private static final String DEFAULT_MASS_HEADER = "mass";
  private static final String DEFAULT_NAME_HEADER = "name";

  private static final String TSV_SEPARATOR = "\t";

  private String metaboliteHeader = null;
  
  private Integer metaboliteIndex;
  private Integer massIndex;
  private Integer nameIndex;

  private File inputFile;

  private MassToRawMetaboliteMap massToMetaboliteMap = new MassToRawMetaboliteMap();

  public Integer getMetaboliteIndex() {
    return metaboliteIndex;
  }

  public Integer getMassIndex() {
    return massIndex;
  }

  public Integer getNameIndex() {
    return nameIndex;
  }

  public MassToRawMetaboliteMap getMassToMoleculeMap() {
    return massToMetaboliteMap;
  }

  MassToRawMetaboliteMapParser() {}

  public MassToRawMetaboliteMapParser(File inputFile) {
    this.inputFile = inputFile;
    try {
      FileChecker.verifyInputFile(inputFile);
      String headerLine = getFormulaeReader(inputFile).readLine();
      List<String> headers = Arrays.asList(headerLine.split(TSV_SEPARATOR));
      validateHeaders(headers);
    } catch (IOException e) {
      throw new RuntimeException("An I/O exception occured when trying to parse input file", e);
    }
  }

  void validateHeaders(List<String> headers) {
    if (headers.contains(DEFAULT_STRUCTURE_HEADER)) {
      this.metaboliteHeader = DEFAULT_STRUCTURE_HEADER;
      this.massToMetaboliteMap.setKind(MassToRawMetaboliteMap.RawMetaboliteKind.INCHI);
    } else if (headers.contains(DEFAULT_FORMULA_HEADER)) {
      this.metaboliteHeader = DEFAULT_FORMULA_HEADER;
      this.massToMetaboliteMap.setKind(MassToRawMetaboliteMap.RawMetaboliteKind.FORMULA);
    } else {
      String msg = String.format("Input file did not contain expected metabolite headers: %s or %s", DEFAULT_FORMULA_HEADER, DEFAULT_STRUCTURE_HEADER);
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
    LOGGER.info("The parser will use the following metabolite header: %s", this.metaboliteHeader);
    this.metaboliteIndex = headers.indexOf(this.metaboliteHeader);

    if (headers.contains(DEFAULT_MASS_HEADER)) {
      String massHeader = DEFAULT_MASS_HEADER;
      this.massIndex = headers.indexOf(massHeader);
      LOGGER.info("The parser detected the following mass header: %s", massHeader);
    } else {
      if (this.metaboliteHeader.equals(DEFAULT_FORMULA_HEADER)) {
        throw new RuntimeException("Masses should be provided if parsing metabolites from formulae.");
      }
      this.massIndex = -1;
      LOGGER.warn("The parser did not detect any mass header. Masses will be computed.");
    }

    if (headers.contains(DEFAULT_NAME_HEADER)) {
      String namesHeader = DEFAULT_NAME_HEADER;
      this.nameIndex = headers.indexOf(namesHeader);
      LOGGER.info("The parser detected the following name header: %s", namesHeader);
    } else {
      this.nameIndex = -1;
      LOGGER.info("The parser did not detect any name header");
    }
  }

  void addRawMetabolite(String line) {

    RawMetabolite rawMetabolite = new RawMetabolite();
    String[] splitLine = line.split(TSV_SEPARATOR);
    String metabolite = splitLine[metaboliteIndex];
    rawMetabolite.setMolecule(metabolite);
    Double mass = null;
    if (massIndex >= 0) {
      mass = Double.parseDouble(splitLine[massIndex]);
      rawMetabolite.setMonoIsotopicMass(mass);
    } else {
      if (metaboliteHeader.equals(DEFAULT_STRUCTURE_HEADER)) {
        try {
          mass = MolImporter.importMol(metabolite).getExactMass();
          rawMetabolite.setMonoIsotopicMass(mass);
        } catch (MolFormatException e) {
          LOGGER.error("Could not parse molecule %s, skipping.", metabolite);
          return;
        }
      }
    }

    if (nameIndex >= 0) {
      String name = splitLine[nameIndex];
      rawMetabolite.setName(name);
    }

    if (mass != null) {
      massToMetaboliteMap.add(mass, rawMetabolite);
    }
  }

  public void parse() throws IOException {

    try (BufferedReader formulaeReader = getFormulaeReader(inputFile)) {

      int i = 0;
      formulaeReader.readLine();

      while (formulaeReader.ready()) {

        String line = formulaeReader.readLine();
        addRawMetabolite(line);
        i++;

        if (i % 1000000 == 0) {
          LOGGER.info("Metabolites processed so far: %d", i);
        }
      }
    }
  }

  private BufferedReader getFormulaeReader(File inchiFile) throws FileNotFoundException {
    FileInputStream formulaeInputStream = new FileInputStream(inchiFile);
    return new BufferedReader(new InputStreamReader(formulaeInputStream));
  }
}
