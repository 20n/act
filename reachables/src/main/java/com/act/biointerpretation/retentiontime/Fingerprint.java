package com.act.biointerpretation.retentiontime;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

public class Fingerprint {
  public static void main(String[] args) throws Exception {
    String inchi = "InChI=1S/C2H4/c1-2/h1-2H2";
    Molecule a = MolImporter.importMol(inchi);
    System.out.println(MolExporter.exportToFormat(a, "smiles"));
  }
}
