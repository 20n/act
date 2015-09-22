package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.io.FileWriter;
import java.util.Iterator;

public class ChemicalCleaner {
  public static enum ChemError {
    BAD_INCHIS,
    UNRESOLVED_R
  }

  NoSQLAPI api;
  Indigo indigo;
  IndigoInchi iinchi;

  public ChemicalCleaner() {
    this.api = new NoSQLAPI();
    this.indigo = new Indigo();
    this.iinchi = new IndigoInchi(this.indigo);
  }

  public Chemical clean(Chemical achem) {
    String inchi = achem.getInChI();

    // if it contains a FAKE, not a true small molecule
    // so SKIP. If it contains a &gt means bad data from
    // wikipedia, also SKIP
    if(inchi.contains("FAKE") || inchi.contains("&gt")) {
      log(achem, ChemError.BAD_INCHIS, inchi);
      return null;
    }

    try {
      IndigoObject mol = iinchi.loadMolecule(inchi);
    } catch(Exception err) {
      // malformed inchi, so SKIP
      log(achem, ChemError.UNRESOLVED_R, inchi);
      return null;
    }

    // passed the tests.. let it go through
    return achem;
  }

  private void log(Chemical chem, ChemError errcode, String error) {
    System.err.println(errcode.toString() + ":\n\tIncoming KG ID: " + chem.getUuid() + "\n\tIncoming KG InChI:" + error);
    //TODO:  append a list somewhere
  }

  // call this function using a file name, to get a dump of
  // bad inchis that don't pass the bio-specific sanitization
  //
  // e.g., String fname = "/home/chris/C/vmwaredata/badinchis.txt";
  //       new ChemicalCleaner().dumpBadToFile(fname);
  private void dumpBadChemicalsToFile(String file) throws Exception {
    FileWriter writer = new FileWriter(file);
    Iterator<Chemical> chems = api.readChemsFromInKnowledgeGraph();
    while(chems.hasNext()) {
      Chemical achem = clean(chems.next());

      if (achem == null)
        continue;

      String inchi = achem.getInChI();
      String name = achem.getFirstName();

      System.out.println(inchi);
      StringBuilder sb = new StringBuilder();
      sb.append(inchi);
      sb.append("\t");
      sb.append(name);
      sb.append("\n");
      writer.write(sb.toString());
    }

    writer.close();
  }

}
