package com.act.biointerpretation.retentiontime;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

public class Fingerprint {

  public static String printMol(String inchi) throws Exception {
    Molecule a = MolImporter.importMol(inchi);
    return MolExporter.exportToFormat(a, "mol");
  }

  public static void main(String[] args) throws Exception {
    System.out.println(printMol("InChI=1S/C2H4/c1-2/h1-2H2"));
    System.out.println(printMol("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"));
    System.out.println(printMol("InChI=1S/C8H11NO2/c9-4-3-6-1-2-7(10)8(11)5-6/h1-2,5,10-11H,3-4,9H2"));
    System.out.println(printMol("InChI=1S/C3H7NO3/c4-2(1-5)3(6)7/h2,5H,1,4H2,(H,6,7)"));
    System.out.println(printMol("InChI=1S/C10H12N2O/c11-4-3-7-6-12-10-2-1-8(13)5-9(7)10/h1-2,5-6,12-13H,3-4,11H2"));
  }
}
