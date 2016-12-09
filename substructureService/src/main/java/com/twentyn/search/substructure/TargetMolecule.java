package com.twentyn.search.substructure;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.commons.csv.CSVRecord;

public class TargetMolecule {
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
}
