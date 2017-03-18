/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package act.installer;

import act.shared.ConsistentInChI;
import act.shared.Chemical;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;


/*
 * Each tab separated:
 * 1. inchi
 * 2. pubchem id
 * 3. inchikey
 * 4. pubchem names
 * 5. SMILES
 * 6. synonyms
 *
 * pubchem names are in csv format
 *   each field tagged with "<type>: " and names separated by ";"
 * SMILES are in csv-like format (fields quoted and separated by commas)
 *   each field tagged with "<type>: "
 * synonyms are in csv-like format (fields quoted and separated by commas)
 *
 * If pubchem id missing, only the inchi should be there.
 * The parser will try to fill out the inchikey and SMILES through indigo.
 *
 * Two different kinds of SMILES:
 * SMILES Canonical: CCCCCCCCC=CCCCCCCCCOCC(COP(=O)(O)OCC[N+](C)(C)C)OC(=O)C
 * SMILES Isomeric: CCCCCCCCC=CCCCCCCCCOC[C@H](COP(=O)(O)OCC[N+](C)(C)C)OC(=O)C
 */

public class ChemicalParser {
  private static boolean DEBUG = false;

  private static Long dummyLong = (long) -1;

  public static Chemical parseLine(String line) {
    String[] fields = line.split("\\t");
    Chemical c = new Chemical(dummyLong); //uuid ignored
    String layered_inchi = fields[0];
    // round trip inchi to make it consistent with the rest of the system
    String inchi = ConsistentInChI.consistentInChI(layered_inchi, "Chemical Parser");
    c.setInchi(inchi);

    setSmilesFromInChI(c,inchi);

    if(fields.length == 1) {
      return c;
    }

    c.setPubchem(Long.parseLong(fields[1]));
    // c.setInchiKey(fields[2]);
    parseSMILES(c,fields[4]);
    parseNames(c,fields[3]);
    parseSynonyms(c,fields[5]);

    if(DEBUG)
      System.out.println(c);

    return c;
  }

  public static void parseNames(Chemical c, String namesStr) {
    if(namesStr.equals("None"))
      return;
    String[] typedNames = parseCSV(namesStr);
    for(String s: typedNames) {
      String[] temp = s.split(":\\s",2);
      if(temp.length < 2) {
        System.err.println("parse pubchem names: " + s + " missing type");
      } else {
        String[] names = temp[1].split(";");
        if(DEBUG) {
          for(String n : names) {
            System.out.println("Names " + temp[0] + ": " + n);
          }
        }
          c.addNames(temp[0],names);
      }
    }
  }

  public static void parseSMILES(Chemical c, String smilesStr) {
    if(smilesStr.equals("None")) {
      System.err.println("pubchem entry missing smiles: " + c.getPubchemID());
      System.exit(1);
    }

    String[] typedSmiles = parseCSV(smilesStr);
    for(String s: typedSmiles) {
      String[] temp = s.split(":\\s",2);
      if(temp.length < 2) {
        System.err.println("parse SMILES: " + s + " missing type");
      } else {
        if(temp[0].equals("Canonical")) {
          if(DEBUG)
            System.out.println("SMILES " + temp[0] + ": " + temp[1]);

          c.setSmiles(temp[1]);
        }

      }
    }
  }

  public static void parseSynonyms(Chemical c, String synonymStr) {
    if(synonymStr.equals("None"))
      return;
    String[] synonyms = parseCSV(synonymStr);
    for(String s : synonyms) {
      String[] temp = s.split(",\\s");
      for(String t : temp) {
        if(DEBUG)
          System.out.println("Synonym: " + t);
        c.addSynonym(t);
      }

    }
  }

  private static String[] parseCSV(String line) {
    String removedEndQuotes = line.substring(1, line.length()-1);
    return removedEndQuotes.split("\",\"");
  }

  private static void setSmilesFromInChI(Chemical c, String inchi) {
      Indigo ind = new Indigo();
      IndigoInchi ic = new IndigoInchi(ind);
      try {
        String smiles = ic.loadMolecule(inchi).canonicalSmiles();
        c.setSmiles(smiles);
      } catch(Exception e) {
        System.out.println("Failed to find SMILES for: " + inchi);
      }
  }

}
