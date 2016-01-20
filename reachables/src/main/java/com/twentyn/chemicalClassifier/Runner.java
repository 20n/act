package com.twentyn.chemicalClassifier;

import uk.ac.cam.ch.wwmm.oscar.Oscar;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.entities.ChemicalStructure;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.entities.FormatType;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.entities.ResolvedNamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A quick and dirty OSCAR classification driver to run over Chris's TSV of chemical names and InChIs.
 */
public class Runner {
  public static void main(String[] args) throws Exception {
    BufferedReader reader = new BufferedReader(new FileReader(args[0]));
    BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]));

    try {
      Oscar oscar = new Oscar();

      String line = null;
      /* NOTE: this is exactly the wrong way to write a TSV reader.  Caveat emptor.
       * See http://tburette.github.io/blog/2014/05/25/so-you-want-to-write-your-own-CSV-code/
       * and then use org.apache.commons.csv.CSVParser instead.
       */
      while ((line = reader.readLine()) != null) {
        // TSV means split on tabs!  Nothing else will do.
        List<String> fields = Arrays.asList(line.split("\t"));
        // Choke if our invariants aren't satisfied.  We expect ever line to have a name and an InChI.
        if (fields.size() != 2) {
          throw new RuntimeException(
              String.format("Found malformed line (all lines must have two fields: %s", line));
        }
        String name = fields.get(1);
        List<ResolvedNamedEntity> entities = oscar.findAndResolveNamedEntities(name);

        System.out.println("**********");
        System.out.println("Name: " + name);
        List<String> outputFields = new ArrayList<>(fields.size() + 1);
        outputFields.addAll(fields);
        if (entities.size() == 0) {
          System.out.println("No match");
          outputFields.add("noMatch");
        } else if (entities.size() == 1) {
          ResolvedNamedEntity entity = entities.get(0);
          NamedEntity ne = entity.getNamedEntity();
          if (ne.getStart() != 0 || ne.getEnd() != name.length()) {
            System.out.println("Partial match");
            printEntity(entity);
            outputFields.add("partialMatch");
          } else {
            System.out.println("Exact match");
            printEntity(entity);
            outputFields.add("exactMatch");
            List<ChemicalStructure> structures = entity.getChemicalStructures(FormatType.STD_INCHI);
            for (ChemicalStructure s : structures) {
              outputFields.add(s.getValue());
            }
          }
        } else { // Multiple matches found!
          System.out.println("Multiple matches");
          for (ResolvedNamedEntity e : entities) {
            printEntity(e);
          }
          outputFields.add("multipleMatches");
        }

        writer.write(String.join("\t", outputFields));
        writer.newLine();
      }
    } finally {
      writer.flush();
      writer.close();
    }
  }

  public static void printEntity(ResolvedNamedEntity e) {
    System.out.println("  " + e.getNamedEntity() + " @ " + e.getNamedEntity().getConfidence());
    System.out.println("    " + e.getChemicalStructures(FormatType.STD_INCHI));
  }
}
