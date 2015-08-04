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
            // NOTE: this is exactly the wrong way to write a TSV reader.  Caveat emptor.
            while ((line = reader.readLine()) != null) {
                List<String> fields = Arrays.asList(line.split("\t"));
                String name = fields.get(1);
                List<ResolvedNamedEntity> entities = oscar.findAndResolveNamedEntities(name);

                System.out.println("**********");
                System.out.println("Name: " + name);
                List<String> outputFields = new ArrayList<String>(fields.size() + 1);
                outputFields.addAll(fields);
                if (entities.size() == 0) {
                    System.out.println("No match");
                    outputFields.add("noMatch");
                } else if (entities.size() > 1) {
                    System.out.println("Multiple matches");
                    for (ResolvedNamedEntity e : entities) {
                        System.out.println("  " + e.getNamedEntity() + " @ " + e.getNamedEntity().getConfidence());
                        System.out.println("    " + e.getChemicalStructures(FormatType.STD_INCHI));
                    }
                    outputFields.add("multipleMatches");
                } else { // One match only.
                    ResolvedNamedEntity entity = entities.get(0);
                    NamedEntity ne = entity.getNamedEntity();
                    if (ne.getStart() != 0 || ne.getEnd() != name.length()) {
                        System.out.println("Partial match");
                        for (ResolvedNamedEntity e : entities) {
                            System.out.println("  " + e.getNamedEntity() + " @ " + e.getNamedEntity().getConfidence());
                            System.out.println("    " + e.getChemicalStructures(FormatType.STD_INCHI));
                        }
                        outputFields.add("partialMatch");
                    } else {
                        System.out.println("Exact match");
                        for (ResolvedNamedEntity e : entities) {
                            System.out.println("  " + e.getNamedEntity() + " @ " + e.getNamedEntity().getConfidence());
                            System.out.println("    " + e.getChemicalStructures(FormatType.STD_INCHI));
                        }
                        outputFields.add("exactMatch");
                        List<ChemicalStructure> structures = entity.getChemicalStructures(FormatType.STD_INCHI);
                        for (ChemicalStructure s : structures) {
                            outputFields.add(s.getValue());
                        }
                    }
                }

                writer.write(String.join("\t", outputFields));
                writer.newLine();
            }
        } finally {
            writer.flush();
            writer.close();
        }
    }

    public static String removeChirality(String inchi) {
        String[] regions = inchi.split("/");
        StringBuilder out = new StringBuilder();
        for(String region : regions) {
            if(region.startsWith("InChI")) {
                out.append(region);
                out.append("/");
            }

            if(region.startsWith("C")) {
                out.append(region);
                out.append("/");
            }

            if(region.startsWith("h")) {
                out.append(region);
                out.append("/");
            }
        }
        return out.toString();
    }

    public static boolean inchiEq(String inchi1, String inchi2) {
        String inchi1stripped = removeChirality(inchi1);
        String inchi2stripped = removeChirality(inchi2);
        return inchi1stripped.equals(inchi2stripped);
    }
}
