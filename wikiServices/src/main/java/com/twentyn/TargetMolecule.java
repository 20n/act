package com.twentyn;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TargetMolecule {
  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();


  Molecule molecule;
  String inchi;
  String displayName;
  String inchiKey;
  String imageName;

  public TargetMolecule(Molecule molecule, String inchi, String displayName, String inchiKey, String imageName) {
    this.molecule = molecule;
    this.inchi = inchi;
    this.displayName = displayName;
    this.inchiKey = inchiKey;
    this.imageName = imageName;
  }

  public Molecule getMolecule() {
    return molecule;
  }

  public String getInchi() {
    return inchi;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public String getImageName() {
    return imageName;
  }

  public static TargetMolecule fromCSVRecord(CSVRecord record) throws MolFormatException {
    String inchi = record.get("inchi");
    String displayName = record.get("display_name");
    String inchiKey = record.get("inchi_key");
    String imageName = record.get("image_name");

    Molecule mol = MolImporter.importMol(inchi);

    return new TargetMolecule(mol, inchi, displayName, inchiKey, imageName);
  }

  public static List<TargetMolecule> loadTargets(File inputFile) throws IOException {
    List<TargetMolecule> results = new ArrayList<>();
    try (CSVParser parser = new CSVParser(new FileReader(inputFile), TSV_FORMAT)) {
      for (CSVRecord record : parser) {
        results.add(TargetMolecule.fromCSVRecord(record));
      }
    }
    return results;
  }

}
